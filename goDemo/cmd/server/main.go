package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"goDemo/internal/config"
	"goDemo/internal/consumer"
	"goDemo/internal/controller"
	"goDemo/internal/middleware"
	"goDemo/internal/model"
	"goDemo/internal/repository"
	"goDemo/internal/service"

	"github.com/gin-gonic/gin"
	"github.com/go-redis/redis/v8"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	"github.com/olivere/elastic/v7"
	"gorm.io/driver/mysql"
	"gorm.io/gorm"
)

func main() {
	// 1. 加载配置
	cfg, err := config.Load("config.yaml")
	if err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}

	// 2. 初始化MySQL
	db, err := initMySQL(cfg)
	if err != nil {
		log.Fatalf("初始化MySQL失败: %v", err)
	}

	// 3. 初始化Redis
	rdb := initRedis(cfg)

	// 4. 初始化MinIO
	minioClient, err := initMinIO(cfg)
	if err != nil {
		log.Fatalf("初始化MinIO失败: %v", err)
	}

	// 5. 初始化Elasticsearch
	esClient, err := initElasticsearch(cfg)
	if err != nil {
		log.Fatalf("初始化Elasticsearch失败: %v", err)
	}

	// 6. 初始化Repository层
	userRepo := repository.NewUserRepository(db)
	fileUploadRepo := repository.NewFileUploadRepository(db)
	chunkInfoRepo := repository.NewChunkInfoRepository(db)
	docVectorRepo := repository.NewDocumentVectorRepository(db)
	convRepo := repository.NewConversationRepository(db)
	orgTagRepo := repository.NewOrganizationTagRepository(db)
	redisRepo := repository.NewRedisRepository(rdb)

	// 7. 初始化Service层
	userService := service.NewUserService(userRepo, orgTagRepo, redisRepo)
	tokenCacheSvc := service.NewTokenCacheService(redisRepo)
	orgTagCacheSvc := service.NewOrgTagCacheService(redisRepo, orgTagRepo, userRepo)
	embeddingClient := service.NewEmbeddingClient(cfg)
	esService := service.NewElasticsearchService(esClient, cfg)
	hybridSearchSvc := service.NewHybridSearchService(esClient, embeddingClient, fileUploadRepo, orgTagCacheSvc, cfg)
	uploadService := service.NewUploadService(minioClient, fileUploadRepo, chunkInfoRepo, redisRepo, cfg)
	parseService := service.NewParseService(docVectorRepo, cfg)
	vectorizationSvc := service.NewVectorizationService(docVectorRepo, esService, embeddingClient, cfg)
	documentService := service.NewDocumentService(fileUploadRepo, docVectorRepo, chunkInfoRepo, esService, orgTagRepo, orgTagCacheSvc, uploadService)
	convService := service.NewConversationService(convRepo, userRepo)
	fileTypeSvc := service.NewFileTypeValidationService()
	deepseek := service.NewDeepSeekClient(cfg)
	chatHandler := service.NewChatHandler(hybridSearchSvc, deepseek, redisRepo, convService)

	// 8. 初始化数据
	service.InitializeAdminUser(db, userRepo, orgTagRepo)
	if err := esService.EnsureIndex(); err != nil {
		log.Printf("初始化ES索引失败: %v", err)
	}

	// 9. 启动Kafka消费者
	kafkaConsumer := consumer.NewFileProcessingConsumer(
		cfg.Kafka.Brokers, cfg.Kafka.Topic, cfg.Kafka.GroupID,
		parseService, vectorizationSvc,
	)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	kafkaConsumer.Start(ctx)

	// 10. 初始化Controller层
	userCtrl := controller.NewUserController(userService, tokenCacheSvc)
	authCtrl := controller.NewAuthController(tokenCacheSvc)
	uploadCtrl := controller.NewUploadController(uploadService, fileTypeSvc, cfg)
	documentCtrl := controller.NewDocumentController(documentService)
	searchCtrl := controller.NewSearchController(hybridSearchSvc)
	chatCtrl := controller.NewChatController(chatHandler)
	convCtrl := controller.NewConversationController(redisRepo, userRepo)
	adminCtrl := controller.NewAdminController(userService, orgTagRepo, redisRepo, convService)
	parseCtrl := controller.NewParseController(parseService)

	// 11. 配置Gin路由
	router := setupRouter(cfg, userCtrl, authCtrl, uploadCtrl, documentCtrl, searchCtrl, chatCtrl, convCtrl, adminCtrl, parseCtrl)

	// 12. 启动HTTP服务器
	srv := &http.Server{
		Addr:    fmt.Sprintf(":%d", cfg.Server.Port),
		Handler: router,
	}

	go func() {
		log.Printf("服务器启动在端口 %d", cfg.Server.Port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("服务器启动失败: %v", err)
		}
	}()

	// 13. 优雅关闭
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Println("正在关闭服务器...")

	cancel()
	kafkaConsumer.Close()

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer shutdownCancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Printf("服务器关闭失败: %v", err)
	}

	log.Println("服务器已关闭")
}

func initMySQL(cfg *config.Config) (*gorm.DB, error) {
	db, err := gorm.Open(mysql.Open(cfg.MySQL.DSN()), &gorm.Config{})
	if err != nil {
		return nil, fmt.Errorf("连接MySQL失败: %w", err)
	}

	sqlDB, err := db.DB()
	if err != nil {
		return nil, err
	}
	sqlDB.SetMaxIdleConns(cfg.MySQL.MaxIdleConns)
	sqlDB.SetMaxOpenConns(cfg.MySQL.MaxOpenConns)

	// 自动迁移
	_ = db.AutoMigrate(
		&model.User{},
		&model.FileUpload{},
		&model.ChunkInfo{},
		&model.DocumentVector{},
		&model.Conversation{},
		&model.OrganizationTag{},
	)

	log.Println("MySQL连接成功")
	return db, nil
}

func initRedis(cfg *config.Config) *redis.Client {
	rdb := redis.NewClient(&redis.Options{
		Addr:     cfg.Redis.Addr(),
		Password: cfg.Redis.Password,
		DB:       cfg.Redis.DB,
	})

	if err := rdb.Ping(context.Background()).Err(); err != nil {
		log.Printf("Redis连接失败: %v (继续启动)", err)
	} else {
		log.Println("Redis连接成功")
	}
	return rdb
}

func initMinIO(cfg *config.Config) (*minio.Client, error) {
	client, err := minio.New(cfg.MinIO.Endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(cfg.MinIO.AccessKey, cfg.MinIO.SecretKey, ""),
		Secure: cfg.MinIO.UseSSL,
	})
	if err != nil {
		return nil, fmt.Errorf("初始化MinIO客户端失败: %w", err)
	}

	// 确保Bucket存在
	ctx := context.Background()
	exists, err := client.BucketExists(ctx, cfg.MinIO.Bucket)
	if err != nil {
		log.Printf("检查MinIO Bucket失败: %v", err)
	}
	if !exists {
		if err := client.MakeBucket(ctx, cfg.MinIO.Bucket, minio.MakeBucketOptions{}); err != nil {
			log.Printf("创建MinIO Bucket失败: %v", err)
		}
	}

	log.Println("MinIO连接成功")
	return client, nil
}

func initElasticsearch(cfg *config.Config) (*elastic.Client, error) {
	client, err := elastic.NewClient(
		elastic.SetURL(cfg.Elasticsearch.URL()),
		elastic.SetBasicAuth(cfg.Elasticsearch.Username, cfg.Elasticsearch.Password),
		elastic.SetSniff(false),
		elastic.SetHealthcheck(false),
	)
	if err != nil {
		return nil, fmt.Errorf("初始化Elasticsearch客户端失败: %w", err)
	}

	log.Println("Elasticsearch连接成功")
	return client, nil
}

func setupRouter(
	cfg *config.Config,
	userCtrl *controller.UserController,
	authCtrl *controller.AuthController,
	uploadCtrl *controller.UploadController,
	documentCtrl *controller.DocumentController,
	searchCtrl *controller.SearchController,
	chatCtrl *controller.ChatController,
	convCtrl *controller.ConversationController,
	adminCtrl *controller.AdminController,
	parseCtrl *controller.ParseController,
) *gin.Engine {
	router := gin.Default()

	// 全局中间件
	router.Use(middleware.CORS())
	router.Use(middleware.Logging())
	router.MaxMultipartMemory = 8 << 20 // 8MB

	// API v1路由组
	v1 := router.Group("/api/v1")

	// 公开路由（不需要认证）
	public := v1.Group("")
	{
		public.POST("/users/register", userCtrl.Register)
		public.POST("/users/login", userCtrl.Login)
		public.GET("/auth/error", authCtrl.Error)
		public.GET("/chat/websocket-token", chatCtrl.GetWebSocketToken)
	}

	// 需要认证的路由
	auth := v1.Group("")
	auth.Use(middleware.JWTAuth())
	{
		// 用户路由
		users := auth.Group("/users")
		{
			users.GET("/me", userCtrl.Me)
			users.GET("/org-tags", userCtrl.GetOrgTags)
			users.PUT("/primary-org", userCtrl.SetPrimaryOrg)
			users.GET("/upload-orgs", userCtrl.GetUploadOrgs)
			users.POST("/logout", userCtrl.Logout)
			users.POST("/logout-all", userCtrl.LogoutAll)
			users.GET("/conversation", convCtrl.GetConversation)
		}

		// 认证路由
		authRoutes := auth.Group("/auth")
		{
			authRoutes.POST("/refreshToken", authCtrl.RefreshToken)
		}

		// 上传路由（需要USER或ADMIN角色）
		upload := auth.Group("/upload")
		{
			upload.POST("/chunk", uploadCtrl.UploadChunk)
			upload.GET("/status", uploadCtrl.GetStatus)
			upload.POST("/merge", uploadCtrl.Merge)
			upload.GET("/supported-types", uploadCtrl.SupportedTypes)
		}

		// 文档路由
		documents := auth.Group("/documents")
		{
			documents.DELETE("/:fileMd5", documentCtrl.Delete)
			documents.GET("/accessible", documentCtrl.Accessible)
			documents.GET("/uploads", documentCtrl.Uploads)
			documents.GET("/download", documentCtrl.Download)
			documents.GET("/preview", documentCtrl.Preview)
		}

		// 搜索路由
		search := auth.Group("/search")
		{
			search.GET("/hybrid", searchCtrl.Hybrid)
		}

		// 解析路由
		parse := auth.Group("/parse")
		{
			parse.POST("/", parseCtrl.Parse)
		}

		// 管理员路由
		admin := auth.Group("/admin")
		admin.Use(middleware.AdminRequired())
		{
			admin.GET("/users", adminCtrl.GetAllUsers)
			admin.GET("/users/list", adminCtrl.GetUsersList)
			admin.POST("/users/create-admin", adminCtrl.CreateAdmin)
			admin.PUT("/users/:userId/org-tags", adminCtrl.AssignOrgTags)
			admin.GET("/org-tags", adminCtrl.GetOrgTags)
			admin.POST("/org-tags", adminCtrl.CreateOrgTag)
			admin.PUT("/org-tags/:tagId", adminCtrl.UpdateOrgTag)
			admin.DELETE("/org-tags/:tagId", adminCtrl.DeleteOrgTag)
			admin.GET("/org-tags/tree", adminCtrl.GetOrgTagTree)
			admin.GET("/system/status", adminCtrl.GetSystemStatus)
			admin.GET("/user-activities", adminCtrl.GetUserActivities)
			admin.GET("/conversation", adminCtrl.GetAdminConversation)
		}
	}

	// WebSocket路由
	router.GET("/chat/:token", chatCtrl.HandleWebSocket)

	return router
}
