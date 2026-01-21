package controller

import (
	"fmt"
	"net/http"
	"strings"

	"goDemo/internal/service"
	"goDemo/internal/utils"

	"github.com/gin-gonic/gin"
)

// AuthController 认证控制器
type AuthController struct {
	tokenCacheSvc *service.TokenCacheService
}

func NewAuthController(tokenCacheSvc *service.TokenCacheService) *AuthController {
	return &AuthController{tokenCacheSvc: tokenCacheSvc}
}

// RefreshToken 刷新Token
func (ctrl *AuthController) RefreshToken(c *gin.Context) {
	var req struct {
		RefreshToken string `json:"refreshToken" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "refreshToken不能为空"})
		return
	}

	claims, err := utils.ParseToken(req.RefreshToken)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "无效的刷新令牌"})
		return
	}

	// 检查令牌是否被拉黑
	valid, err := ctrl.tokenCacheSvc.IsTokenValid(claims.TokenID)
	if err != nil || !valid {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "令牌已失效"})
		return
	}

	// 生成新的Token对
	newAccessToken, newAccessID, err := utils.GenerateAccessToken(
		claims.UserID, claims.Role, claims.OrgTags, claims.PrimaryOrg,
	)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "生成令牌失败"})
		return
	}

	newRefreshToken, newRefreshID, err := utils.GenerateRefreshToken(
		claims.UserID, claims.Role, claims.OrgTags, claims.PrimaryOrg,
	)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "生成令牌失败"})
		return
	}

	// 拉黑旧的刷新令牌
	_ = ctrl.tokenCacheSvc.InvalidateToken(claims.TokenID)

	// 缓存新Token
	_ = ctrl.tokenCacheSvc.CacheAccessToken(newAccessID, claims.UserID)
	_ = ctrl.tokenCacheSvc.CacheRefreshToken(newRefreshID, claims.UserID)

	c.JSON(http.StatusOK, gin.H{
		"accessToken":  newAccessToken,
		"refreshToken": newRefreshToken,
	})
}

// Error 测试错误响应
func (ctrl *AuthController) Error(c *gin.Context) {
	code := c.DefaultQuery("code", "500")
	msg := c.DefaultQuery("msg", "未知错误")
	var statusCode int
	fmt.Sscanf(code, "%d", &statusCode)
	if statusCode == 0 {
		statusCode = 500
	}
	c.JSON(statusCode, gin.H{"error": msg})
}
