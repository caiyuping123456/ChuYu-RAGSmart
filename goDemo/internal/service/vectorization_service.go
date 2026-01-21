package service

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"

	"goDemo/internal/config"
	"goDemo/internal/middleware/exception"
	"goDemo/internal/model"
	"goDemo/internal/repository"
	"goDemo/internal/utils"
)

// EmbeddingClient 向量化客户端（在service包内，避免与client包循环引用）
type EmbeddingClient struct {
	cfg        *config.Config
	httpClient *http.Client
}

func NewEmbeddingClient(cfg *config.Config) *EmbeddingClient {
	return &EmbeddingClient{cfg: cfg, httpClient: &http.Client{}}
}

type embeddingRequest struct {
	Model string   `json:"model"`
	Input []string `json:"input"`
}

type embeddingResponse struct {
	Data []struct {
		Embedding []float32 `json:"embedding"`
		Index     int       `json:"index"`
	} `json:"data"`
}

// Embed 批量向量化
func (c *EmbeddingClient) Embed(texts []string) ([][]float32, error) {
	reqBody := embeddingRequest{Model: c.cfg.Embedding.Model, Input: texts}
	jsonData, err := json.Marshal(reqBody)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequest("POST", c.cfg.Embedding.URL+"/embeddings", strings.NewReader(string(jsonData)))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+c.cfg.Embedding.APIKey)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("embedding API error %d: %s", resp.StatusCode, string(body))
	}

	var embResp embeddingResponse
	if err := json.Unmarshal(body, &embResp); err != nil {
		return nil, err
	}

	result := make([][]float32, len(embResp.Data))
	for _, item := range embResp.Data {
		result[item.Index] = item.Embedding
	}
	return result, nil
}

// EmbedSingle 单条向量化
func (c *EmbeddingClient) EmbedSingle(text string) ([]float32, error) {
	results, err := c.Embed([]string{text})
	if err != nil {
		return nil, err
	}
	if len(results) == 0 {
		return nil, fmt.Errorf("no embedding returned")
	}
	return results[0], nil
}

// VectorizationService 向量化服务
type VectorizationService struct {
	docVectorRepo  *repository.DocumentVectorRepository
	esService      *ElasticsearchService
	embeddingClient *EmbeddingClient
	cfg            *config.Config
}

func NewVectorizationService(
	docVectorRepo *repository.DocumentVectorRepository,
	esService *ElasticsearchService,
	embeddingClient *EmbeddingClient,
	cfg *config.Config,
) *VectorizationService {
	return &VectorizationService{
		docVectorRepo:   docVectorRepo,
		esService:       esService,
		embeddingClient: embeddingClient,
		cfg:             cfg,
	}
}

// Vectorize 向量化指定文件的文本分块
func (s *VectorizationService) Vectorize(fileMd5, userID, orgTag string, isPublic bool) error {
	// 获取文本分块
	chunks, err := s.docVectorRepo.FindTextChunksByFileMd5(fileMd5)
	if err != nil {
		return exception.NewInternal(fmt.Sprintf("获取文本分块失败: %v", err))
	}
	if len(chunks) == 0 {
		return exception.NewNotFound("未找到文本分块")
	}

	// 批量向量化
	var allEsDocs []model.EsDocument
	batchSize := s.cfg.Embedding.BatchSize
	for i := 0; i < len(chunks); i += batchSize {
		end := i + batchSize
		if end > len(chunks) {
			end = len(chunks)
		}

		batch := chunks[i:end]
		texts := make([]string, len(batch))
		for j, c := range batch {
			texts[j] = c.Content
		}

		vectors, err := s.embeddingClient.Embed(texts)
		if err != nil {
			utils.ErrorLog("批量向量化失败: %v", err)
			continue
		}

		for j, chunk := range batch {
			if j >= len(vectors) {
				break
			}
			doc := model.EsDocument{
				ID:           fmt.Sprintf("%s_%d", fileMd5, chunk.ChunkID),
				FileMd5:      fileMd5,
				ChunkID:      chunk.ChunkID,
				TextContent:  chunk.Content,
				Vector:       vectors[j],
				ModelVersion: s.cfg.Embedding.Model,
				UserID:       userID,
				OrgTag:       orgTag,
				IsPublic:     isPublic,
			}
			allEsDocs = append(allEsDocs, doc)
		}
	}

	// 批量索引到ES
	if len(allEsDocs) > 0 {
		if err := s.esService.BulkIndex(allEsDocs); err != nil {
			return exception.NewInternal(fmt.Sprintf("索引到ES失败: %v", err))
		}
	}

	utils.BusinessLog("向量化完成 fileMd5=%s, 共%d个文档", fileMd5, len(allEsDocs))
	return nil
}
