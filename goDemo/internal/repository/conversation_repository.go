package repository

import (
	"goDemo/internal/model"
	"time"

	"gorm.io/gorm"
)

// ConversationRepository 对话记录数据访问
type ConversationRepository struct {
	db *gorm.DB
}

func NewConversationRepository(db *gorm.DB) *ConversationRepository {
	return &ConversationRepository{db: db}
}

func (r *ConversationRepository) Create(conversation *model.Conversation) error {
	return r.db.Create(conversation).Error
}

func (r *ConversationRepository) FindByUserID(userID uint64) ([]model.Conversation, error) {
	var conversations []model.Conversation
	if err := r.db.Where("user_id = ?", userID).Order("timestamp DESC").Find(&conversations).Error; err != nil {
		return nil, err
	}
	return conversations, nil
}

func (r *ConversationRepository) FindByUserIDAndDateRange(userID uint64, start, end time.Time) ([]model.Conversation, error) {
	var conversations []model.Conversation
	if err := r.db.Where("user_id = ? AND timestamp BETWEEN ? AND ?", userID, start, end).
		Order("timestamp DESC").Find(&conversations).Error; err != nil {
		return nil, err
	}
	return conversations, nil
}

func (r *ConversationRepository) FindAll() ([]model.Conversation, error) {
	var conversations []model.Conversation
	if err := r.db.Order("timestamp DESC").Find(&conversations).Error; err != nil {
		return nil, err
	}
	return conversations, nil
}

func (r *ConversationRepository) FindByUserIDPaginated(userID uint64, page, size int) ([]model.Conversation, int64, error) {
	var conversations []model.Conversation
	var total int64

	query := r.db.Model(&model.Conversation{}).Where("user_id = ?", userID)
	if err := query.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	offset := (page - 1) * size
	if err := query.Offset(offset).Limit(size).Order("timestamp DESC").Find(&conversations).Error; err != nil {
		return nil, 0, err
	}
	return conversations, total, nil
}

func (r *ConversationRepository) FindAllWithFilter(userID uint64, start, end *time.Time) ([]model.Conversation, error) {
	var conversations []model.Conversation
	query := r.db.Order("timestamp DESC")

	if userID > 0 {
		query = query.Where("user_id = ?", userID)
	}
	if start != nil {
		query = query.Where("timestamp >= ?", start)
	}
	if end != nil {
		query = query.Where("timestamp <= ?", end)
	}

	if err := query.Find(&conversations).Error; err != nil {
		return nil, err
	}
	return conversations, nil
}
