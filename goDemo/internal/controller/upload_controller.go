package controller

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"

	"goDemo/internal/config"
	"goDemo/internal/model"
	"goDemo/internal/service"

	"github.com/gin-gonic/gin"
	"github.com/segmentio/kafka-go"
)

// UploadController 文件上传控制器
type UploadController struct {
	uploadService   *service.UploadService
	fileTypeService *service.FileTypeValidationService
	kafkaWriter     *kafka.Writer
}

func NewUploadController(
	uploadService *service.UploadService,
	fileTypeService *service.FileTypeValidationService,
	cfg *config.Config,
) *UploadController {
	writer := &kafka.Writer{
		Addr:         kafka.TCP(cfg.Kafka.Brokers...),
		Topic:        cfg.Kafka.Topic,
		Balancer:     &kafka.LeastBytes{},
		RequiredAcks: kafka.RequireAll,
		MaxAttempts:  3,
	}

	return &UploadController{
		uploadService:   uploadService,
		fileTypeService: fileTypeService,
		kafkaWriter:     writer,
	}
}

// UploadChunk 上传文件分块
func (ctrl *UploadController) UploadChunk(c *gin.Context) {
	userID, _ := c.Get("userId")
	role, _ := c.Get("role")

	fileMd5 := c.PostForm("file_md5")
	chunkIndexStr := c.PostForm("chunk_index")
	totalSizeStr := c.PostForm("total_size")
	fileName := c.PostForm("file_name")
	totalChunksStr := c.DefaultPostForm("total_chunks", "1")
	orgTag := c.DefaultPostForm("org_tag", "")
	isPublicStr := c.DefaultPostForm("is_public", "false")

	if fileMd5 == "" || chunkIndexStr == "" || fileName == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "缺少必要参数"})
		return
	}

	chunkIndex, _ := strconv.Atoi(chunkIndexStr)
	totalSize, _ := strconv.ParseInt(totalSizeStr, 10, 64)
	totalChunks, _ := strconv.Atoi(totalChunksStr)
	isPublic := isPublicStr == "true"

	// 默认使用用户的主组织
	if orgTag == "" {
		orgTag, _ = c.Get("primaryOrg")
		if orgTag == nil {
			orgTag = "DEFAULT"
		}
		orgTag = orgTag.(string)
	}

	// 验证文件类型（第一个分块时验证）
	if chunkIndex == 0 {
		if !ctrl.fileTypeService.IsSupported(fileName) {
			c.JSON(http.StatusBadRequest, gin.H{"error": "不支持的文件类型"})
			return
		}
	}

	file, err := c.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "文件上传失败"})
		return
	}

	uploadedChunks, err := ctrl.uploadService.UploadChunk(
		fileMd5, chunkIndex, totalSize, fileName,
		totalChunks, orgTag, isPublic,
		userID.(string), file,
	)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	progress := calculateProgress(uploadedChunks, totalChunks)

	c.JSON(http.StatusOK, gin.H{
		"uploadedChunks": uploadedChunks,
		"progress":      progress,
		"fileType":      getFileType(fileName),
	})
}

// GetStatus 获取上传状态
func (ctrl *UploadController) GetStatus(c *gin.Context) {
	userID, _ := c.Get("userId")
	fileMd5 := c.Query("file_md5")
	if fileMd5 == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "file_md5不能为空"})
		return
	}

	// 假设总共有100个分块（实际应从请求获取）
	totalChunks := 100
	uploadedChunks, err := ctrl.uploadService.GetUploadedChunks(userID.(string), fileMd5, totalChunks)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	progress := calculateProgress(uploadedChunks, totalChunks)

	c.JSON(http.StatusOK, gin.H{
		"uploadedChunks": uploadedChunks,
		"progress":      progress,
	})
}

// Merge 合并分块
func (ctrl *UploadController) Merge(c *gin.Context) {
	userID, _ := c.Get("userId")
	role, _ := c.Get("role")

	var req struct {
		FileMd5  string `json:"fileMd5" binding:"required"`
		FileName string `json:"fileName" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	// 验证文件归属
	file, err := ctrl.uploadService.GetFileByMd5AndUserID(req.FileMd5, userID.(string))
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "文件不存在或不属于当前用户"})
		return
	}

	// 合并分块
	presignedURL, err := ctrl.uploadService.MergeChunks(req.FileMd5, req.FileName, userID.(string))
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// 发送到Kafka进行异步处理
	task := model.FileProcessingTask{
		FileMd5:  req.FileMd5,
		FilePath: fmt.Sprintf("merged/%s", req.FileName),
		FileName: req.FileName,
		UserID:   userID.(string),
		OrgTag:   file.OrgTag,
		IsPublic: file.IsPublic,
	}
	taskJSON, _ := json.Marshal(task)
	_ = ctrl.kafkaWriter.WriteMessages(c.Request.Context(), kafka.Message{Value: taskJSON})

	c.JSON(http.StatusOK, gin.H{
		"message": "文件合并成功",
		"url":     presignedURL,
	})
}

// SupportedTypes 获取支持的文件类型
func (ctrl *UploadController) SupportedTypes(c *gin.Context) {
	types := ctrl.fileTypeService.GetSupportedTypes()
	c.JSON(http.StatusOK, gin.H{"supportedTypes": types})
}

func calculateProgress(uploaded []int, total int) int {
	if total == 0 {
		return 100
	}
	return len(uploaded) * 100 / total
}

func getFileType(fileName string) string {
	ext := ""
	if idx := lastDot(fileName); idx >= 0 {
		ext = fileName[idx:]
	}
	switch ext {
	case ".pdf":
		return "PDF文档"
	case ".doc", ".docx":
		return "Word文档"
	case ".xls", ".xlsx":
		return "Excel表格"
	case ".ppt", ".pptx":
		return "PPT演示文稿"
	case ".txt":
		return "文本文件"
	case ".md":
		return "Markdown文档"
	default:
		return "其他文件"
	}
}

func lastDot(s string) int {
	for i := len(s) - 1; i >= 0; i-- {
		if s[i] == '.' {
			return i
		}
	}
	return -1
}
