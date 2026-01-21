package service

import (
	"fmt"
	"strings"

	"goDemo/internal/middleware/exception"
	"goDemo/internal/model"
	"goDemo/internal/repository"
	"goDemo/internal/utils"
)

// DocumentService 文档服务
type DocumentService struct {
	fileUploadRepo *repository.FileUploadRepository
	docVectorRepo  *repository.DocumentVectorRepository
	chunkInfoRepo  *repository.ChunkInfoRepository
	esService      *ElasticsearchService
	orgTagRepo     *repository.OrganizationTagRepository
	orgTagCache    *OrgTagCacheService
	uploadService  *UploadService
}

func NewDocumentService(
	fileUploadRepo *repository.FileUploadRepository,
	docVectorRepo *repository.DocumentVectorRepository,
	chunkInfoRepo *repository.ChunkInfoRepository,
	esService *ElasticsearchService,
	orgTagRepo *repository.OrganizationTagRepository,
	orgTagCache *OrgTagCacheService,
	uploadService *UploadService,
) *DocumentService {
	return &DocumentService{
		fileUploadRepo: fileUploadRepo,
		docVectorRepo:  docVectorRepo,
		chunkInfoRepo:  chunkInfoRepo,
		esService:      esService,
		orgTagRepo:     orgTagRepo,
		orgTagCache:    orgTagCache,
		uploadService:  uploadService,
	}
}

// DeleteDocument 删除文档
func (s *DocumentService) DeleteDocument(fileMd5, userID, role string) error {
	fileUpload, err := s.fileUploadRepo.FindByFileMd5(fileMd5)
	if err != nil {
		return exception.NewNotFound("文件不存在")
	}

	// 只有文件所有者或管理员可以删除
	if fileUpload.UserID != userID && role != model.RoleAdmin {
		return exception.NewForbidden("无权删除该文件")
	}

	// 删除ES中的向量
	if err := s.esService.DeleteByFileMd5(fileMd5); err != nil {
		utils.ErrorLog("删除ES向量失败: %v", err)
	}

	// 删除MinIO中的文件
	objectName := fmt.Sprintf("merged/%s", fileUpload.FileName)
	if err := s.uploadService.DeleteFile(objectName); err != nil {
		utils.ErrorLog("删除MinIO文件失败: %v", err)
	}

	// 删除文档向量记录
	if err := s.docVectorRepo.DeleteByFileMd5(fileMd5); err != nil {
		utils.ErrorLog("删除文档向量记录失败: %v", err)
	}

	// 删除分块信息
	if err := s.chunkInfoRepo.DeleteByFileMd5(fileMd5); err != nil {
		utils.ErrorLog("删除分块信息失败: %v", err)
	}

	// 删除文件上传记录
	if err := s.fileUploadRepo.DeleteByFileMd5(fileMd5); err != nil {
		utils.ErrorLog("删除文件上传记录失败: %v", err)
	}

	utils.FileOperationLog("delete", fileMd5)
	return nil
}

// GetAccessibleFiles 获取用户可访问的文件
func (s *DocumentService) GetAccessibleFiles(userID string) ([]model.FileUpload, error) {
	effectiveTags, err := s.orgTagCache.GetEffectiveTags(userID)
	if err != nil {
		utils.WarnLog("获取有效标签失败: %v", err)
		effectiveTags = []string{}
	}
	return s.fileUploadRepo.FindAccessibleFiles(userID, effectiveTags)
}

// GetUserUploadedFiles 获取用户上传的文件
func (s *DocumentService) GetUserUploadedFiles(userID string) ([]model.FileUpload, error) {
	return s.fileUploadRepo.FindByUserID(userID)
}

// GenerateDownloadURL 生成下载URL
func (s *DocumentService) GenerateDownloadURL(fileName string) (string, error) {
	objectName := fmt.Sprintf("merged/%s", fileName)
	return s.uploadService.GenerateDownloadURL(objectName)
}

// GetFilePreviewContent 获取文件预览内容
func (s *DocumentService) GetFilePreviewContent(fileName string) (string, error) {
	objectName := fmt.Sprintf("merged/%s", fileName)
	return s.uploadService.GetFileContent(objectName)
}

// GetOrgTagName 获取组织标签名称
func (s *DocumentService) GetOrgTagName(tagID string) string {
	tag, err := s.orgTagRepo.FindByTagID(tagID)
	if err != nil {
		return tagID
	}
	return tag.Name
}

// FormatFileUpload 格式化文件上传信息
func (s *DocumentService) FormatFileUpload(fu *model.FileUpload) map[string]interface{} {
	return map[string]interface{}{
		"id":         fu.ID,
		"fileMd5":    fu.FileMd5,
		"fileName":   fu.FileName,
		"totalSize":  fu.TotalSize,
		"status":     fu.Status,
		"userId":     fu.UserID,
		"orgTag":     fu.OrgTag,
		"orgTagName": s.GetOrgTagName(fu.OrgTag),
		"isPublic":   fu.IsPublic,
		"createdAt":  fu.CreatedAt,
		"mergedAt":   fu.MergedAt,
	}
}

// FormatFileUploads 批量格式化
func (s *DocumentService) FormatFileUploads(files []model.FileUpload) []map[string]interface{} {
	result := make([]map[string]interface{}, 0, len(files))
	for i := range files {
		result = append(result, s.FormatFileUpload(&files[i]))
	}
	return result
}
