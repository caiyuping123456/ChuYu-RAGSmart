package repository

import (
	"goDemo/internal/model"

	"gorm.io/gorm"
)

// OrganizationTagRepository 组织标签数据访问
type OrganizationTagRepository struct {
	db *gorm.DB
}

func NewOrganizationTagRepository(db *gorm.DB) *OrganizationTagRepository {
	return &OrganizationTagRepository{db: db}
}

func (r *OrganizationTagRepository) Create(tag *model.OrganizationTag) error {
	return r.db.Create(tag).Error
}

func (r *OrganizationTagRepository) FindByTagID(tagID string) (*model.OrganizationTag, error) {
	var tag model.OrganizationTag
	if err := r.db.Where("tag_id = ?", tagID).First(&tag).Error; err != nil {
		return nil, err
	}
	return &tag, nil
}

func (r *OrganizationTagRepository) FindAll() ([]model.OrganizationTag, error) {
	var tags []model.OrganizationTag
	if err := r.db.Find(&tags).Error; err != nil {
		return nil, err
	}
	return tags, nil
}

func (r *OrganizationTagRepository) Update(tag *model.OrganizationTag) error {
	return r.db.Save(tag).Error
}

func (r *OrganizationTagRepository) Delete(tagID string) error {
	return r.db.Where("tag_id = ?", tagID).Delete(&model.OrganizationTag{}).Error
}

func (r *OrganizationTagRepository) ExistsByTagID(tagID string) (bool, error) {
	var count int64
	if err := r.db.Model(&model.OrganizationTag{}).Where("tag_id = ?", tagID).Count(&count).Error; err != nil {
		return false, err
	}
	return count > 0, nil
}

func (r *OrganizationTagRepository) FindByParentTag(parentTag string) ([]model.OrganizationTag, error) {
	var tags []model.OrganizationTag
	if err := r.db.Where("parent_tag = ?", parentTag).Find(&tags).Error; err != nil {
		return nil, err
	}
	return tags, nil
}
