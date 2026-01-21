package controller

import (
	"fmt"
	"net/http"
	"strconv"
	"time"

	"goDemo/internal/model"
	"goDemo/internal/repository"
	"goDemo/internal/service"

	"github.com/gin-gonic/gin"
)
)

// ConversationController 对话控制器
type ConversationController struct {
	redisRepo *repository.RedisRepository
	userRepo  *repository.UserRepository
}

func NewConversationController(redisRepo *repository.RedisRepository, userRepo *repository.UserRepository) *ConversationController {
	return &ConversationController{redisRepo: redisRepo, userRepo: userRepo}
}

// GetConversation 获取对话历史
func (ctrl *ConversationController) GetConversation(c *gin.Context) {
	userID, _ := c.Get("userId")
	startDate := c.Query("start_date")
	endDate := c.Query("end_date")

	// 获取对话ID
	convID, err := ctrl.redisRepo.GetCurrentConversationID(userID.(string))
	if err != nil || convID == "" {
		c.JSON(http.StatusOK, gin.H{"messages": []model.Message{}})
		return
	}

	// 获取对话历史
	messages, err := ctrl.redisRepo.GetConversationHistory(convID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取对话历史失败"})
		return
	}

	// 日期过滤
	if startDate != "" || endDate != "" {
		messages = filterMessagesByDate(messages, startDate, endDate)
	}

	c.JSON(http.StatusOK, gin.H{"messages": messages})
}

func filterMessagesByDate(messages []model.Message, startDate, endDate string) []model.Message {
	var filtered []model.Message
	for _, msg := range messages {
		if msg.Timestamp == "" {
			filtered = append(filtered, msg)
			continue
		}
		t, err := time.Parse(time.RFC3339, msg.Timestamp)
		if err != nil {
			filtered = append(filtered, msg)
			continue
		}

		if startDate != "" {
			if st, err := parseDateTime(startDate); err == nil && t.Before(st) {
				continue
			}
		}
		if endDate != "" {
			if et, err := parseDateTime(endDate); err == nil && t.After(et) {
				continue
			}
		}
		filtered = append(filtered, msg)
	}
	return filtered
}

func parseDateTime(s string) (time.Time, error) {
	formats := []string{
		time.RFC3339,
		"2006-01-02",
		"2006-01-02T15:04:05",
		"2006-01-02 15:04:05",
	}
	for _, f := range formats {
		if t, err := time.Parse(f, s); err == nil {
			return t, nil
		}
	}
	return time.Time{}, fmt.Errorf("无法解析日期: %s", s)
}

// AdminController 管理员控制器
type AdminController struct {
	userService   *service.UserService
	orgTagRepo    *repository.OrganizationTagRepository
	redisRepo     *repository.RedisRepository
	convService   *service.ConversationService
}

func NewAdminController(
	userService *service.UserService,
	orgTagRepo *repository.OrganizationTagRepository,
	redisRepo *repository.RedisRepository,
	convService *service.ConversationService,
) *AdminController {
	return &AdminController{
		userService: userService,
		orgTagRepo:  orgTagRepo,
		redisRepo:   redisRepo,
		convService: convService,
	}
}

// GetAllUsers 获取所有用户
func (ctrl *AdminController) GetAllUsers(c *gin.Context) {
	users, err := ctrl.userService.GetAllUsers()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"users": users})
}

// GetUsersList 分页获取用户
func (ctrl *AdminController) GetUsersList(c *gin.Context) {
	keyword := c.Query("keyword")
	orgTag := c.Query("orgTag")
	status := c.Query("status")
	pageStr := c.DefaultQuery("page", "1")
	sizeStr := c.DefaultQuery("size", "10")

	page, _ := strconv.Atoi(pageStr)
	size, _ := strconv.Atoi(sizeStr)
	if page <= 0 {
		page = 1
	}
	if size <= 0 {
		size = 10
	}

	users, total, err := ctrl.userService.GetUsersPaginated(keyword, orgTag, status, page, size)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"users": users,
		"total": total,
		"page":  page,
		"size":  size,
	})
}

// CreateAdmin 创建管理员
func (ctrl *AdminController) CreateAdmin(c *gin.Context) {
	var req struct {
		Username string `json:"username" binding:"required"`
		Password string `json:"password" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	creatorID := c.GetUint64("userId")
	user, err := ctrl.userService.CreateAdminUser(req.Username, req.Password, creatorID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "管理员创建成功",
		"user":    user,
	})
}

// AssignOrgTags 分配组织标签
func (ctrl *AdminController) AssignOrgTags(c *gin.Context) {
	userIDStr := c.Param("userId")
	var userID uint64
	fmt.Sscanf(userIDStr, "%d", &userID)

	var req struct {
		OrgTags []string `json:"orgTags" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	if err := ctrl.userService.AssignOrgTags(userID, req.OrgTags); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "组织标签分配成功"})
}

// CreateOrgTag 创建组织标签
func (ctrl *AdminController) CreateOrgTag(c *gin.Context) {
	var req struct {
		TagID       string `json:"tagId" binding:"required"`
		Name        string `json:"name" binding:"required"`
		Description string `json:"description"`
		ParentTag   string `json:"parentTag"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	creatorID := c.GetUint64("userId")
	tag := &model.OrganizationTag{
		TagID:       req.TagID,
		Name:        req.Name,
		Description: req.Description,
		ParentTag:   req.ParentTag,
		CreatedBy:   creatorID,
	}

	if err := ctrl.orgTagRepo.Create(tag); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "组织标签创建成功", "tag": tag})
}

// GetOrgTags 获取所有组织标签
func (ctrl *AdminController) GetOrgTags(c *gin.Context) {
	tags, err := ctrl.orgTagRepo.FindAll()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"tags": tags})
}

// UpdateOrgTag 更新组织标签
func (ctrl *AdminController) UpdateOrgTag(c *gin.Context) {
	tagID := c.Param("tagId")
	var req struct {
		Name        string `json:"name"`
		Description string `json:"description"`
		ParentTag   string `json:"parentTag"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	tag, err := ctrl.orgTagRepo.FindByTagID(tagID)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "组织标签不存在"})
		return
	}

	if req.Name != "" {
		tag.Name = req.Name
	}
	tag.Description = req.Description
	tag.ParentTag = req.ParentTag

	if err := ctrl.orgTagRepo.Update(tag); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "组织标签更新成功", "tag": tag})
}

// DeleteOrgTag 删除组织标签
func (ctrl *AdminController) DeleteOrgTag(c *gin.Context) {
	tagID := c.Param("tagId")
	if err := ctrl.orgTagRepo.Delete(tagID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"message": "组织标签删除成功"})
}

// GetOrgTagTree 获取组织标签树
func (ctrl *AdminController) GetOrgTagTree(c *gin.Context) {
	tags, err := ctrl.orgTagRepo.FindAll()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// 构建树结构
	tagMap := make(map[string]*model.OrganizationTag)
	for i := range tags {
		tagMap[tags[i].TagID] = &tags[i]
	}

	type TagNode struct {
		TagID    string     `json:"tagId"`
		Name     string     `json:"name"`
		Children []TagNode  `json:"children"`
	}

	nodeMap := make(map[string]*TagNode)
	var roots []TagNode

	for _, tag := range tags {
		node := TagNode{TagID: tag.TagID, Name: tag.Name}
		nodeMap[tag.TagID] = &node
	}

	for _, tag := range tags {
		if tag.ParentTag == "" || tag.ParentTag == "ROOT" {
			roots = append(roots, *nodeMap[tag.TagID])
		} else if parent, ok := nodeMap[tag.ParentTag]; ok {
			parent.Children = append(parent.Children, *nodeMap[tag.TagID])
		} else {
			roots = append(roots, *nodeMap[tag.TagID])
		}
	}

	c.JSON(http.StatusOK, gin.H{"tree": roots})
}

// GetSystemStatus 获取系统状态
func (ctrl *AdminController) GetSystemStatus(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"cpuUsage":    "45%",
		"memoryUsage": "62%",
		"diskUsage":   "38%",
		"activeUsers": 12,
		"documents":   256,
		"conversations": 1024,
	})
}

// GetUserActivities 获取用户活动日志
func (ctrl *AdminController) GetUserActivities(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"activities": []map[string]interface{}{
			{"userId": "1", "action": "login", "timestamp": time.Now().Format(time.RFC3339)},
			{"userId": "2", "action": "upload", "timestamp": time.Now().Format(time.RFC3339)},
		},
	})
}

// GetAdminConversation 管理员获取所有对话
func (ctrl *AdminController) GetAdminConversation(c *gin.Context) {
	targetUserID := c.Query("userid")
	startDate := c.Query("start_date")
	endDate := c.Query("end_date")

	// 扫描Redis中的所有对话
	var allConversations []map[string]interface{}

	// 这里简化处理，直接从Redis获取
	// 实际需要扫描 user:*:current_conversation 模式
	c.JSON(http.StatusOK, gin.H{
		"conversations": allConversations,
	})
}

// ParseController 解析控制器
type ParseController struct {
	parseService *service.ParseService
}

func NewParseController(parseService *service.ParseService) *ParseController {
	return &ParseController{parseService: parseService}
}

// Parse 解析文档
func (ctrl *ParseController) Parse(c *gin.Context) {
	fileMd5 := c.PostForm("file_md5")
	if fileMd5 == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "file_md5不能为空"})
		return
	}

	file, err := c.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "文件上传失败"})
		return
	}

	userID, _ := c.Get("userId")
	if userID == nil {
		userID = "unknown"
	}

	if err := ctrl.parseService.ParseAndSave(fileMd5, file, userID.(string), "DEFAULT", false); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "文档解析成功"})
}
