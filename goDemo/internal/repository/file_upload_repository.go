package repository

import (
	"goDemo/internal/model"

	"gorm.io/gorm"
)

// FileUploadRepository 文件上传数据访问
type FileUploadRepository struct {
	db *gorm.DB
}

func NewFileUploadRepository(db *gorm.DB) *FileUploadRepository {
	return &FileUploadRepository{db: db}
}

func (r *FileUploadRepository) Create(fileUpload *model.FileUpload) error {
	return r.db.Create(fileUpload).Error
}

func (r *FileUploadRepository) FindByFileMd5AndUserID(fileMd5, userID string) (*model.FileUpload, error) {
	var fu model.FileUpload
	if err := r.db.Where("file_md5 = ? AND user_id = ?", fileMd5, userID).First(&fu).Error; err != nil {
		return nil, err
	}
	return &fu, nil
}

func (r *FileUploadRepository) FindByFileMd5(fileMd5 string) (*model.FileUpload, error) {
	var fu model.FileUpload
	if err := r.db.Where("file_md5 = ?", fileMd5).First(&fu).Error; err != nil {
		return nil, err
	}
	return &fu, nil
}

func (r *FileUploadRepository) Update(fileUpload *model.FileUpload) error {
	return r.db.Save(fileUpload).Error
}

func (r *FileUploadRepository) DeleteByFileMd5(fileMd5 string) error {
	return r.db.Where("file_md5 = ?", fileMd5).Delete(&model.FileUpload{}).Error
}

// FindAccessibleFiles 查找用户可访问的文件（私有+公开+同组织）
func (r *FileUploadRepository) FindAccessibleFiles(userID string, orgTags []string) ([]model.FileUpload, error) {
	var files []model.FileUpload
	query := r.db.Where("user_id = ? OR is_public = ?", userID, true)
	if len(orgTags) > 0 {
		query = query.Or("org_tag IN ?", orgTags)
	}
	if err := query.Find(&files).Error; err != nil {
		return nil, err
	}
	return files, nil
}

// FindByUserID 查找用户上传的文件
func (r *FileUploadRepository) FindByUserID(userID string) ([]model.FileUpload, error) {
	var files []model.FileUpload
	if err := r.db.Where("user_id = ?", userID).Find(&files).Error; err != nil {
		return nil, err
	}
	return files, nil
}

// FindByFileName 通过文件名查找
func (r *FileUploadRepository) FindByFileName(fileName string) (*model.FileUpload, error) {
	var fu model.FileUpload
	if err := r.db.Where("file_name = ?", fileName).First(&fu).Error; err != nil {
		return nil, err
	}
	return &fu, nil
}

// FindByFileNameAndPublic 查找公开文件
func (r *FileUploadRepository) FindByFileNameAndPublic(fileName string) (*model.FileUpload, error) {
	var fu model.FileUpload
	if err := r.db.Where("file_name = ? AND is_public = ?", fileName, true).First(&fu).Error; err != nil {
		return nil, err
	}
	return &fu, nil
}

// FindByFileMd5WithOrgTags 按orgTag查找文件
func (r *FileUploadRepository) FindByFileMd5WithOrgTags(fileMd5 string, orgTags []string) (*model.FileUpload, error) {
	var fu model.FileUpload
	query := r.db.Where("file_md5 = ?", fileMd5)
	if err := query.First(&fu).Error; err != nil {
		return nil, err
	}
	return &fu, nil
}
