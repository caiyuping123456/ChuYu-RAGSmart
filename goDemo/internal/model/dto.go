package model

// EsDocument Elasticsearch文档实体
type EsDocument struct {
	ID           string    `json:"id"`
	FileMd5      string    `json:"fileMd5"`
	ChunkID      int       `json:"chunkId"`
	TextContent  string    `json:"textContent"`
	Vector       []float32 `json:"vector"`
	ModelVersion string    `json:"modelVersion"`
	UserID       string    `json:"userId"`
	OrgTag       string    `json:"orgTag"`
	IsPublic     bool      `json:"isPublic"`
}

// SearchRequest 搜索请求
type SearchRequest struct {
	Query string `json:"query" form:"query"`
	TopK  int    `json:"topK" form:"topK"`
}

// SearchResult 搜索结果
type SearchResult struct {
	FileMd5     string  `json:"fileMd5"`
	ChunkID     int     `json:"chunkId"`
	TextContent string  `json:"textContent"`
	Score       float64 `json:"score"`
	FileName    string  `json:"fileName"`
	UserID      string  `json:"userId"`
	OrgTag      string  `json:"orgTag"`
	IsPublic    bool    `json:"isPublic"`
}

// Message 聊天消息
type Message struct {
	Role      string `json:"role"`
	Content   string `json:"content"`
	Timestamp string `json:"timestamp,omitempty"`
}

// TextChunk 文本分块
type TextChunk struct {
	ChunkID int    `json:"chunkId"`
	Content string `json:"content"`
}
