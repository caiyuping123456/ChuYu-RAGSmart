package middleware

import (
	"time"

	"github.com/gin-gonic/gin"
)

// StartTime 记录请求开始时间的中间件
func StartTime() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Set("startTime", time.Now())
		c.Next()
	}
}

// GetStartTime 从context获取开始时间并返回elapsed
func GetStartTime(c *gin.Context) (time.Duration, bool) {
	if t, exists := c.Get("startTime"); exists {
		if start, ok := t.(time.Time); ok {
			return time.Since(start), true
		}
	}
	return 0, false
}
