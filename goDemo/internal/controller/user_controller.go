package controller

import (
	"fmt"
	"net/http"
	"strings"

	"goDemo/internal/middleware/exception"
	"goDemo/internal/service"
	"goDemo/internal/utils"

	"github.com/gin-gonic/gin"
)

// UserController 用户控制器
type UserController struct {
	userService    *service.UserService
	tokenCacheSvc  *service.TokenCacheService
}

func NewUserController(userService *service.UserService, tokenCacheSvc *service.TokenCacheService) *UserController {
	return &UserController{userService: userService, tokenCacheSvc: tokenCacheSvc}
}

// Register 用户注册
func (ctrl *UserController) Register(c *gin.Context) {
	var req struct {
		Username string `json:"username" binding:"required"`
		Password string `json:"password" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "用户名和密码不能为空"})
		return
	}

	user, err := ctrl.userService.RegisterUser(req.Username, req.Password)
	if err != nil {
		if ce, ok := err.(*exception.CustomException); ok {
			c.JSON(ce.Code, gin.H{"error": ce.Message})
		} else {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		}
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "注册成功",
		"user": gin.H{
			"id":       user.ID,
			"username": user.Username,
			"role":     user.Role,
		},
	})
}

// Login 用户登录
func (ctrl *UserController) Login(c *gin.Context) {
	var req struct {
		Username string `json:"username" binding:"required"`
		Password string `json:"password" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "用户名和密码不能为空"})
		return
	}

	user, err := ctrl.userService.AuthenticateUser(req.Username, req.Password)
	if err != nil {
		if ce, ok := err.(*exception.CustomException); ok {
			c.JSON(ce.Code, gin.H{"error": ce.Message})
		} else {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		}
		return
	}

	// 生成Token对
	accessToken, accessID, err := utils.GenerateAccessToken(
		fmt.Sprintf("%d", user.ID), user.Role, user.OrgTags, user.PrimaryOrg,
	)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "生成令牌失败"})
		return
	}

	refreshToken, refreshID, err := utils.GenerateRefreshToken(
		fmt.Sprintf("%d", user.ID), user.Role, user.OrgTags, user.PrimaryOrg,
	)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "生成令牌失败"})
		return
	}

	// 缓存Token
	_ = ctrl.tokenCacheSvc.CacheAccessToken(accessID, fmt.Sprintf("%d", user.ID))
	_ = ctrl.tokenCacheSvc.CacheRefreshToken(refreshID, fmt.Sprintf("%d", user.ID))

	c.JSON(http.StatusOK, gin.H{
		"accessToken":  accessToken,
		"refreshToken": refreshToken,
		"user": gin.H{
			"id":         user.ID,
			"username":   user.Username,
			"role":       user.Role,
			"orgTags":    splitTags(user.OrgTags),
			"primaryOrg": user.PrimaryOrg,
		},
	})
}

// Me 获取当前用户信息
func (ctrl *UserController) Me(c *gin.Context) {
	userID, _ := c.Get("userId")
	user, err := ctrl.userService.GetUserByID(parseUserID(userID.(string)))
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "用户不存在"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"id":         user.ID,
		"username":   user.Username,
		"role":       user.Role,
		"orgTags":    splitTags(user.OrgTags),
		"primaryOrg": user.PrimaryOrg,
		"createdAt":  user.CreatedAt,
		"updatedAt":  user.UpdatedAt,
	})
}

// GetOrgTags 获取用户组织标签
func (ctrl *UserController) GetOrgTags(c *gin.Context) {
	userID, _ := c.Get("userId")
	tags, err := ctrl.userService.GetUserOrgTags(userID.(string))
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"orgTags": tags})
}

// SetPrimaryOrg 设置主组织
func (ctrl *UserController) SetPrimaryOrg(c *gin.Context) {
	userID, _ := c.Get("userId")
	var req struct {
		PrimaryOrg string `json:"primaryOrg" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	if err := ctrl.userService.SetUserPrimaryOrg(userID.(string), req.PrimaryOrg); err != nil {
		if ce, ok := err.(*exception.CustomException); ok {
			c.JSON(ce.Code, gin.H{"error": ce.Message})
		} else {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		}
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "主组织设置成功"})
}

// GetUploadOrgs 获取上传用的组织标签
func (ctrl *UserController) GetUploadOrgs(c *gin.Context) {
	userID, _ := c.Get("userId")
	tags, err := ctrl.userService.GetUserOrgTags(userID.(string))
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	primaryOrg, _ := c.Get("primaryOrg")
	c.JSON(http.StatusOK, gin.H{
		"orgTags":    tags,
		"primaryOrg": primaryOrg,
	})
}

// Logout 用户登出
func (ctrl *UserController) Logout(c *gin.Context) {
	tokenID, _ := c.Get("tokenId")
	if tokenID != nil {
		_ = ctrl.tokenCacheSvc.InvalidateToken(tokenID.(string))
	}
	c.JSON(http.StatusOK, gin.H{"message": "登出成功"})
}

// LogoutAll 登出所有设备
func (ctrl *UserController) LogoutAll(c *gin.Context) {
	userID, _ := c.Get("userId")
	_ = ctrl.tokenCacheSvc.InvalidateAllUserTokens(userID.(string))
	c.JSON(http.StatusOK, gin.H{"message": "所有设备已登出"})
}

// helper
func splitTags(tags string) []string {
	if tags == "" {
		return []string{}
	}
	result := []string{}
	for _, t := range strings.Split(tags, ",") {
		t = strings.TrimSpace(t)
		if t != "" {
			result = append(result, t)
		}
	}
	return result
}

func parseUserID(s string) uint64 {
	var id uint64
	fmt.Sscanf(s, "%d", &id)
	return id
}
