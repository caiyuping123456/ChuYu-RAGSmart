package middleware

import (
	"net/http"
	"strings"
	"time"

	"goDemo/internal/utils"

	"github.com/gin-gonic/gin"
)

// JWTAuth JWT认证中间件
func JWTAuth() gin.HandlerFunc {
	return func(c *gin.Context) {
		path := c.Request.URL.Path

		// 不需要认证的路径
		publicPaths := map[string]bool{
			"/api/v1/users/register": true,
			"/api/v1/users/login":    true,
		}

		// WebSocket路径
		if strings.HasPrefix(path, "/chat/") || strings.HasPrefix(path, "/ws/") {
			c.Next()
			return
		}

		// 公开API
		if publicPaths[path] {
			c.Next()
			return
		}

		// 从Header获取Token
		authHeader := c.GetHeader("Authorization")
		if authHeader == "" {
			// 尝试从query参数获取（用于下载/预览）
			if token := c.Query("token"); token != "" {
				authHeader = "Bearer " + token
			}
		}

		if authHeader == "" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "未提供认证令牌"})
			c.Abort()
			return
		}

		// 解析Bearer Token
		parts := strings.SplitN(authHeader, " ", 2)
		if len(parts) != 2 || parts[0] != "Bearer" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "认证令牌格式错误"})
			c.Abort()
			return
		}

		tokenStr := parts[1]
		claims, err := utils.ParseToken(tokenStr)
		if err != nil {
			// 检查是否在宽限期内
			if utils.IsTokenExpiredButInGrace(claims) {
				// 尝试刷新
				newToken, _, err := utils.GenerateAccessToken(claims.UserID, claims.Role, claims.OrgTags, claims.PrimaryOrg)
				if err == nil {
					c.Header("New-Token", newToken)
					setClaimsToContext(c, claims)
					c.Next()
					return
				}
			}
			c.JSON(http.StatusUnauthorized, gin.H{"error": "无效的认证令牌"})
			c.Abort()
			return
		}

		// 检查Token是否即将过期，自动刷新
		if utils.IsTokenExpiringSoon(claims) {
			newToken, _, err := utils.GenerateAccessToken(claims.UserID, claims.Role, claims.OrgTags, claims.PrimaryOrg)
			if err == nil {
				c.Header("New-Token", newToken)
			}
		}

		setClaimsToContext(c, claims)
		c.Next()
	}
}

func setClaimsToContext(c *gin.Context, claims *utils.JWTClaims) {
	c.Set("userId", claims.UserID)
	c.Set("role", claims.Role)
	c.Set("username", claims.Subject)
	c.Set("orgTags", claims.OrgTags)
	c.Set("primaryOrg", claims.PrimaryOrg)
	c.Set("tokenId", claims.TokenID)
}

// RoleRequired 角色验证中间件
func RoleRequired(roles ...string) gin.HandlerFunc {
	return func(c *gin.Context) {
		role, exists := c.Get("role")
		if !exists {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "未认证"})
			c.Abort()
			return
		}
		userRole := role.(string)
		for _, r := range roles {
			if userRole == r {
				c.Next()
				return
			}
		}
		c.JSON(http.StatusForbidden, gin.H{"error": "权限不足"})
		c.Abort()
	}
}

// AdminRequired 管理员权限中间件
func AdminRequired() gin.HandlerFunc {
	return RoleRequired("ADMIN")
}

// UserOrAdminRequired 用户或管理员权限中间件
func UserOrAdminRequired() gin.HandlerFunc {
	return RoleRequired("USER", "ADMIN")
}

// OrgTagAuth 组织标签权限中间件（提取请求属性）
func OrgTagAuth() gin.HandlerFunc {
	return func(c *gin.Context) {
		// 从JWT中提取用户信息设置到请求属性
		// 具体的资源级别权限在各自的service中处理
		c.Next()
	}
}

// CORS 跨域中间件
func CORS() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Authorization, Content-Type, New-Token")
		c.Header("Access-Control-Expose-Headers", "New-Token")
		c.Header("Access-Control-Max-Age", "86400")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}
		c.Next()
	}
}

// Logging 请求日志中间件
func Logging() gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		c.Next()
		duration := time.Since(start)
		if duration.Seconds() > 3 {
			utils.WarnLog("慢请求: %s %s 耗时: %v", c.Request.Method, c.Request.URL.Path, duration)
		} else {
			utils.PerformanceLog(c.Request.Method+" "+c.Request.URL.Path, duration)
		}
	}
}
