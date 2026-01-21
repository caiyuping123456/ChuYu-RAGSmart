package model

import (
	"time"
)

// User 用户实体，对应 users 表
type User struct {
	ID         uint64    `gorm:"primaryKey;autoIncrement" json:"id"`
	Username   string    `gorm:"uniqueIndex;not null;size:255" json:"username"`
	Password   string    `gorm:"not null;size:255" json:"-"`
	Role       string    `gorm:"not null;size:20" json:"role"` // USER, ADMIN
	OrgTags    string    `gorm:"column:org_tags;size:500" json:"orgTags"`
	PrimaryOrg string    `gorm:"column:primary_org;size:100" json:"primaryOrg"`
	CreatedAt  time.Time `gorm:"autoCreateTime" json:"createdAt"`
	UpdatedAt  time.Time `gorm:"autoUpdateTime" json:"updatedAt"`
}

func (User) TableName() string {
	return "users"
}

const (
	RoleUser  = "USER"
	RoleAdmin = "ADMIN"
)

// FileUpload 文件上传实体，对应 file_upload 表
type FileUpload struct {
	ID        uint64     `gorm:"primaryKey;autoIncrement" json:"id"`
	FileMd5   string     `gorm:"column:file_md5;not null;size:32" json:"fileMd5"`
	FileName  string     `gorm:"size:500" json:"fileName"`
	TotalSize int64      `json:"totalSize"`
	Status    int        `json:"status"` // 0-上传中 1-已完成
	UserID    string     `gorm:"column:user_id;not null;size:64" json:"userId"`
	OrgTag    string     `gorm:"column:org_tag;size:100" json:"orgTag"`
	IsPublic  bool       `gorm:"column:is_public;not null;default:false" json:"isPublic"`
	CreatedAt time.Time  `gorm:"autoCreateTime" json:"createdAt"`
	MergedAt  *time.Time `json:"mergedAt"`
}

func (FileUpload) TableName() string {
	return "file_upload"
}

// ChunkInfo 文件分块信息实体，对应 chunk_info 表
type ChunkInfo struct {
	ID          uint64 `gorm:"primaryKey;autoIncrement" json:"id"`
	FileMd5     string `gorm:"size:32" json:"fileMd5"`
	ChunkIndex  int    `json:"chunkIndex"`
	ChunkMd5    string `gorm:"size:32" json:"chunkMd5"`
	StoragePath string `gorm:"size:500" json:"storagePath"`
}

func (ChunkInfo) TableName() string {
	return "chunk_info"
}

// DocumentVector 文档向量实体，对应 document_vectors 表
type DocumentVector struct {
	VectorID     uint64 `gorm:"primaryKey;autoIncrement" json:"vectorId"`
	FileMd5      string `gorm:"not null;size:32" json:"fileMd5"`
	ChunkID      int    `gorm:"not null" json:"chunkId"`
	TextContent  string `gorm:"type:longtext" json:"textContent"`
	ModelVersion string `gorm:"size:32" json:"modelVersion"`
	UserID       string `gorm:"column:user_id;not null;size:64" json:"userId"`
	OrgTag       string `gorm:"column:org_tag;size:50" json:"orgTag"`
	IsPublic     bool   `gorm:"column:is_public;not null;default:false" json:"isPublic"`
}

func (DocumentVector) TableName() string {
	return "document_vectors"
}

// Conversation 对话记录实体，对应 conversations 表
type Conversation struct {
	ID        uint64    `gorm:"primaryKey;autoIncrement" json:"id"`
	UserID    uint64    `gorm:"not null;index:idx_user_id" json:"userId"`
	Question  string    `gorm:"not null;type:text" json:"question"`
	Answer    string    `gorm:"not null;type:text" json:"answer"`
	Timestamp time.Time `gorm:"index:idx_timestamp" json:"timestamp"`
}

func (Conversation) TableName() string {
	return "conversations"
}

// OrganizationTag 组织标签实体，对应 organization_tags 表
type OrganizationTag struct {
	TagID      string    `gorm:"column:tag_id;primaryKey;size:100" json:"tagId"`
	Name       string    `gorm:"not null;size:255" json:"name"`
	Description string   `gorm:"type:text" json:"description"`
	ParentTag  string    `gorm:"column:parent_tag;size:255" json:"parentTag"`
	CreatedBy  uint64    `gorm:"column:created_by;not null" json:"createdBy"`
	CreatedAt  time.Time `gorm:"autoCreateTime" json:"createdAt"`
	UpdatedAt  time.Time `gorm:"autoUpdateTime" json:"updatedAt"`
}

func (OrganizationTag) TableName() string {
	return "organization_tags"
}

// FileProcessingTask Kafka文件处理任务DTO
type FileProcessingTask struct {
	FileMd5  string `json:"fileMd5"`
	FilePath string `json:"filePath"`
	FileName string `json:"fileName"`
	UserID   string `json:"userId"`
	OrgTag   string `json:"orgTag"`
	IsPublic bool   `json:"isPublic"`
}
