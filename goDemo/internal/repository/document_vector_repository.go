package repository

import (
	"goDemo/internal/model"

	"gorm.io/gorm"
)

// DocumentVectorRepository 文档向量数据访问
type DocumentVectorRepository struct {
	db *gorm.DB
}

func NewDocumentVectorRepository(db *gorm.DB) *DocumentVectorRepository {
	return &DocumentVectorRepository{db: db}
}

func (r *DocumentVectorRepository) Create(doc *model.DocumentVector) error {
	return r.db.Create(doc).Error
}

func (r *DocumentVectorRepository) BatchCreate(docs []model.DocumentVector) error {
	return r.db.CreateInBatches(docs, 100).Error
}

func (r *DocumentVectorRepository) FindByFileMd5(fileMd5 string) ([]model.DocumentVector, error) {
	var docs []model.DocumentVector
	if err := r.db.Where("file_md5 = ?", fileMd5).Find(&docs).Error; err != nil {
		return nil, err
	}
	return docs, nil
}

func (r *DocumentVectorRepository) DeleteByFileMd5(fileMd5 string) error {
	return r.db.Where("file_md5 = ?", fileMd5).Delete(&model.DocumentVector{}).Error
}

// FindTextChunksByFileMd5 获取文本分块用于向量化
func (r *DocumentVectorRepository) FindTextChunksByFileMd5(fileMd5 string) ([]model.TextChunk, error) {
	var docs []model.DocumentVector
	if err := r.db.Where("file_md5 = ?", fileMd5).Find(&docs).Error; err != nil {
		return nil, err
	}

	chunks := make([]model.TextChunk, 0, len(docs))
	for _, doc := range docs {
		chunks = append(chunks, model.TextChunk{
			ChunkID: doc.ChunkID,
			Content: doc.TextContent,
		})
	}
	return chunks, nil
}
