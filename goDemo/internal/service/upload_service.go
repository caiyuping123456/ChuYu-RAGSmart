package service

import (
	"context"
	"fmt"
	"io"
	"log"
	"mime/multipart"
	"strconv"

	"goDemo/internal/config"
	"goDemo/internal/middleware/exception"
	"goDemo/internal/model"
	"goDemo/internal/repository"
	"goDemo/internal/utils"

	"github.com/minio/minio-go/v7"
)

// UploadService 文件上传服务
type UploadService struct {
	minioClient  *minio.Client
	fileUploadRepo *repository.FileUploadRepository
	chunkInfoRepo  *repository.ChunkInfoRepository
	redisRepo      *repository.RedisRepository
	cfg            *config.Config
}

func NewUploadService(
	minioClient *minio.Client,
	fileUploadRepo *repository.FileUploadRepository,
	chunkInfoRepo *repository.ChunkInfoRepository,
	redisRepo *repository.RedisRepository,
	cfg *config.Config,
) *UploadService {
	return &UploadService{
		minioClient:    minioClient,
		fileUploadRepo: fileUploadRepo,
		chunkInfoRepo:  chunkInfoRepo,
		redisRepo:      redisRepo,
		cfg:            cfg,
	}
}

// UploadChunk 上传文件分块
func (s *UploadService) UploadChunk(
	fileMd5 string, chunkIndex int, totalSize int64, fileName string,
	totalChunks int, orgTag string, isPublic bool, userID string,
	fileHeader *multipart.FileHeader,
) ([]int, error) {
	// 检查分块是否已上传
	uploaded, err := s.redisRepo.IsChunkUploaded(userID, fileMd5, chunkIndex)
	if err != nil {
		return nil, exception.NewInternal("检查分块状态失败")
	}
	if uploaded {
		// 返回已上传列表
		allUploaded, _ := s.redisRepo.GetUploadedChunks(userID, fileMd5, totalChunks)
		return allUploaded, nil
	}

	// 首次上传时创建文件记录
	existing, _ := s.fileUploadRepo.FindByFileMd5AndUserID(fileMd5, userID)
	if existing == nil {
		fileUpload := &model.FileUpload{
			FileMd5:   fileMd5,
			FileName:  fileName,
			TotalSize: totalSize,
			Status:    0,
			UserID:    userID,
			OrgTag:    orgTag,
			IsPublic:  isPublic,
		}
		if err := s.fileUploadRepo.Create(fileUpload); err != nil {
			return nil, exception.NewInternal("创建文件记录失败")
		}
	}

	// 上传分块到MinIO
	objectName := fmt.Sprintf("chunks/%s/%d", fileMd5, chunkIndex)
	file, err := fileHeader.Open()
	if err != nil {
		return nil, exception.NewInternal("打开文件失败")
	}
	defer file.Close()

	_, err = s.minioClient.PutObject(
		context.Background(),
		s.cfg.MinIO.Bucket,
		objectName,
		file,
		fileHeader.Size,
		minio.PutObjectOptions{ContentType: fileHeader.Header.Get("Content-Type")},
	)
	if err != nil {
		return nil, exception.NewInternal(fmt.Sprintf("上传分块到MinIO失败: %v", err))
	}

	// 记录分块信息
	chunk := &model.ChunkInfo{
		FileMd5:     fileMd5,
		ChunkIndex:  chunkIndex,
		StoragePath: objectName,
	}
	if err := s.chunkInfoRepo.Create(chunk); err != nil {
		return nil, exception.NewInternal("保存分块信息失败")
	}

	// 标记Redis位图
	if err := s.redisRepo.MarkChunkUploaded(userID, fileMd5, chunkIndex); err != nil {
		return nil, exception.NewInternal("标记分块状态失败")
	}

	utils.FileOperationLog("upload_chunk", fileMd5, "chunkIndex="+strconv.Itoa(chunkIndex))

	// 返回所有已上传分块
	allUploaded, err := s.redisRepo.GetUploadedChunks(userID, fileMd5, totalChunks)
	if err != nil {
		return nil, exception.NewInternal("获取上传进度失败")
	}
	return allUploaded, nil
}

// MergeChunks 合并分块
func (s *UploadService) MergeChunks(fileMd5, fileName, userID string) (string, error) {
	// 查找文件记录
	fileUpload, err := s.fileUploadRepo.FindByFileMd5AndUserID(fileMd5, userID)
	if err != nil {
		return "", exception.NewNotFound("文件记录不存在")
	}

	// 获取所有分块
	chunks, err := s.chunkInfoRepo.FindByFileMd5OrderByChunkIndex(fileMd5)
	if err != nil || len(chunks) == 0 {
		return "", exception.NewBadRequest("没有找到已上传的分块")
	}

	// 使用MinIO ComposeObject合并
	destObjectName := fmt.Sprintf("merged/%s", fileName)

	// 创建合并源
	var sources []minio.CopySrcOptions
	for _, chunk := range chunks {
		sources = append(sources, minio.CopySrcOptions{
			Bucket: s.cfg.MinIO.Bucket,
			Object: chunk.StoragePath,
		})
	}

	// 合并
	_, err = s.minioClient.ComposeObject(
		context.Background(),
		minio.CopyDestOptions{
			Bucket: s.cfg.MinIO.Bucket,
			Object: destObjectName,
		},
		sources...,
	)
	if err != nil {
		return "", exception.NewInternal(fmt.Sprintf("合并文件失败: %v", err))
	}

	// 清理分块文件
	for _, chunk := range chunks {
		_ = s.minioClient.RemoveObject(context.Background(), s.cfg.MinIO.Bucket, chunk.StoragePath, minio.RemoveObjectOptions{})
	}

	// 清除Redis位图
	_ = s.redisRepo.DeleteUploadBitmap(userID, fileMd5)

	// 更新文件状态
	fileUpload.Status = 1
	if err := s.fileUploadRepo.Update(fileUpload); err != nil {
		return "", exception.NewInternal("更新文件状态失败")
	}

	// 生成预签名URL
	presignedURL, err := s.minioClient.PresignedGetObject(
		context.Background(),
		s.cfg.MinIO.Bucket,
		destObjectName,
		1*60*60*1000000000, // 1 hour
		nil,
	)
	if err != nil {
		log.Printf("生成预签名URL失败: %v", err)
		return destObjectName, nil
	}

	utils.FileOperationLog("merge", fileMd5, "fileName="+fileName)
	return presignedURL.String(), nil
}

// GetUploadedChunks 获取已上传的分块列表
func (s *UploadService) GetUploadedChunks(userID, fileMd5 string, totalChunks int) ([]int, error) {
	return s.redisRepo.GetUploadedChunks(userID, fileMd5, totalChunks)
}

// GetFileByMd5AndUserID 根据MD5和用户ID查找文件
func (s *UploadService) GetFileByMd5AndUserID(fileMd5, userID string) (*model.FileUpload, error) {
	return s.fileUploadRepo.FindByFileMd5AndUserID(fileMd5, userID)
}

// GetFileTypeMap 获取支持的文件类型
func (s *UploadService) GetFileTypeMap() map[string]string {
	return map[string]string{
		".pdf":  "PDF文档",
		".doc":  "Word文档",
		".docx": "Word文档",
		".xls":  "Excel表格",
		".xlsx": "Excel表格",
		".ppt":  "PPT演示文稿",
		".pptx": "PPT演示文稿",
		".txt":  "文本文件",
		".md":   "Markdown文档",
		".csv":  "CSV表格",
		".json": "JSON文件",
		".xml":  "XML文件",
		".html": "HTML文件",
		".jpg":  "图片",
		".jpeg": "图片",
		".png":  "图片",
		".gif":  "图片",
	}
}

// GenerateDownloadURL 生成下载URL
func (s *UploadService) GenerateDownloadURL(objectName string) (string, error) {
	reqParams := make(map[string][]string)
	presignedURL, err := s.minioClient.PresignedGetObject(
		context.Background(),
		s.cfg.MinIO.Bucket,
		objectName,
		1*60*60*1000000000, // 1 hour
		reqParams,
	)
	if err != nil {
		return "", exception.NewInternal(fmt.Sprintf("生成下载URL失败: %v", err))
	}
	return presignedURL.String(), nil
}

// GetFileContent 获取文件内容（预览）
func (s *UploadService) GetFileContent(objectName string) (string, error) {
	obj, err := s.minioClient.GetObject(context.Background(), s.cfg.MinIO.Bucket, objectName, minio.GetObjectOptions{})
	if err != nil {
		return "", exception.NewInternal(fmt.Sprintf("获取文件失败: %v", err))
	}
	defer obj.Close()

	buf := make([]byte, 10240) // 10KB
	n, err := obj.Read(buf)
	if err != nil && err != io.EOF {
		return "", exception.NewInternal(fmt.Sprintf("读取文件内容失败: %v", err))
	}
	return string(buf[:n]), nil
}

// DeleteFile 从MinIO删除文件
func (s *UploadService) DeleteFile(objectName string) error {
	return s.minioClient.RemoveObject(context.Background(), s.cfg.MinIO.Bucket, objectName, minio.RemoveObjectOptions{})
}
