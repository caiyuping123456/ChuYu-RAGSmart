package controller

import (
	"net/http"

	"goDemo/internal/middleware/exception"
	"goDemo/internal/service"

	"github.com/gin-gonic/gin"
)

// DocumentController 文档控制器
type DocumentController struct {
	documentService *service.DocumentService
}

func NewDocumentController(documentService *service.DocumentService) *DocumentController {
	return &DocumentController{documentService: documentService}
}

// Delete 删除文档
func (ctrl *DocumentController) Delete(c *gin.Context) {
	fileMd5 := c.Param("fileMd5")
	userID, _ := c.Get("userId")
	role, _ := c.Get("role")

	if err := ctrl.documentService.DeleteDocument(fileMd5, userID.(string), role.(string)); err != nil {
		if ce, ok := err.(*exception.CustomException); ok {
			c.JSON(ce.Code, gin.H{"error": ce.Message})
		} else {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		}
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "文档删除成功"})
}

// Accessible 获取可访问的文件
func (ctrl *DocumentController) Accessible(c *gin.Context) {
	userID, _ := c.Get("userId")

	files, err := ctrl.documentService.GetAccessibleFiles(userID.(string))
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"files": ctrl.documentService.FormatFileUploads(files),
	})
}

// Uploads 获取用户上传的文件
func (ctrl *DocumentController) Uploads(c *gin.Context) {
	userID, _ := c.Get("userId")

	files, err := ctrl.documentService.GetUserUploadedFiles(userID.(string))
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"files": ctrl.documentService.FormatFileUploads(files),
	})
}

// Download 下载文件
func (ctrl *DocumentController) Download(c *gin.Context) {
	fileName := c.Query("fileName")
	if fileName == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "fileName不能为空"})
		return
	}

	url, err := ctrl.documentService.GenerateDownloadURL(fileName)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"url": url})
}

// Preview 预览文件
func (ctrl *DocumentController) Preview(c *gin.Context) {
	fileName := c.Query("fileName")
	if fileName == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "fileName不能为空"})
		return
	}

	content, err := ctrl.documentService.GetFilePreviewContent(fileName)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"content": content})
}
