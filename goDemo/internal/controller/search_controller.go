package controller

import (
	"net/http"
	"strconv"

	"goDemo/internal/service"

	"github.com/gin-gonic/gin"
)

// SearchController 搜索控制器
type SearchController struct {
	hybridSearch *service.HybridSearchService
}

func NewSearchController(hybridSearch *service.HybridSearchService) *SearchController {
	return &SearchController{hybridSearch: hybridSearch}
}

// Hybrid 混合搜索
func (ctrl *SearchController) Hybrid(c *gin.Context) {
	query := c.Query("query")
	if query == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "query参数不能为空"})
		return
	}

	topKStr := c.DefaultQuery("topK", "10")
	topK, _ := strconv.Atoi(topKStr)
	if topK <= 0 {
		topK = 10
	}

	userID, exists := c.Get("userId")
	if exists {
		results, err := ctrl.hybridSearch.SearchWithPermission(query, topK, userID.(string))
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, gin.H{"results": results})
	} else {
		results, err := ctrl.hybridSearch.Search(query, topK)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, gin.H{"results": results})
	}
}
