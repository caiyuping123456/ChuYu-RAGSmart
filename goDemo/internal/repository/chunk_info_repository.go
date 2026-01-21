package repository

import (
	"goDemo/internal/model"

	"gorm.io/gorm"
)

// ChunkInfoRepository 文件分块数据访问
type ChunkInfoRepository struct {
	db *gorm.DB
}

func NewChunkInfoRepository(db *gorm.DB) *ChunkInfoRepository {
	return &ChunkInfoRepository{db: db}
}

func (r *ChunkInfoRepository) Create(chunk *model.ChunkInfo) error {
	return r.db.Create(chunk).Error
}

func (r *ChunkInfoRepository) FindByFileMd5OrderByChunkIndex(fileMd5 string) ([]model.ChunkInfo, error) {
	var chunks []model.ChunkInfo
	if err := r.db.Where("file_md5 = ?", fileMd5).Order("chunk_index ASC").Find(&chunks).Error; err != nil {
		return nil, err
	}
	return chunks, nil
}

func (r *ChunkInfoRepository) DeleteByFileMd5(fileMd5 string) error {
	return r.db.Where("file_md5 = ?", fileMd5).Delete(&model.ChunkInfo{}).Error
}

func (r *ChunkInfoRepository) CountByFileMd5(fileMd5 string) (int64, error) {
	var count int64
	if err := r.db.Model(&model.ChunkInfo{}).Where("file_md5 = ?", fileMd5).Count(&count).Error; err != nil {
		return 0, err
	}
	return count, nil
}
