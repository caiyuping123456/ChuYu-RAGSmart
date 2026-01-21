package service

import (
	"bytes"
	"context"
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

	"github.com/olivere/elastic/v7"
)

// ElasticsearchService ES索引服务
type ElasticsearchService struct {
	client *elastic.Client
	cfg    *config.Config
}

func NewElasticsearchService(client *elastic.Client, cfg *config.Config) *ElasticsearchService {
	return &ElasticsearchService{client: client, cfg: cfg}
}

// BulkIndex 批量索引文档
func (s *ElasticsearchService) BulkIndex(documents []model.EsDocument) error {
	if len(documents) == 0 {
		return nil
	}

	bulkRequest := s.client.Bulk()
	indexName := s.cfg.Elasticsearch.IndexName

	for _, doc := range documents {
		req := elastic.NewBulkIndexRequest().Index(indexName).Id(doc.ID).Doc(doc)
		bulkRequest = bulkRequest.Add(req)
	}

	bulkResponse, err := bulkRequest.Do(context.Background())
	if err != nil {
		return fmt.Errorf("bulk index failed: %w", err)
	}

	if bulkResponse.Errors {
		for _, item := range bulkResponse.Failed() {
			utils.ErrorLog("索引失败 ID=%s: %s", item.Id, item.Error.Reason)
		}
		return fmt.Errorf("部分文档索引失败")
	}

	utils.BusinessLog("批量索引 %d 个文档成功", len(documents))
	return nil
}

// DeleteByFileMd5 按文件MD5删除文档
func (s *ElasticsearchService) DeleteByFileMd5(fileMd5 string) error {
	indexName := s.cfg.Elasticsearch.IndexName
	_, err := s.client.DeleteByQuery(indexName).
		Query(elastic.NewTermQuery("fileMd5", fileMd5)).
		Do(context.Background())
	if err != nil {
		return fmt.Errorf("delete by fileMd5 failed: %w", err)
	}
	utils.FileOperationLog("es_delete", fileMd5)
	return nil
}

// EnsureIndex 确保索引存在
func (s *ElasticsearchService) EnsureIndex() error {
	indexName := s.cfg.Elasticsearch.IndexName
	exists, err := s.client.IndexExists(indexName).Do(context.Background())
	if err != nil {
		return fmt.Errorf("check index exists failed: %w", err)
	}
	if exists {
		return nil
	}

	mapping := `{
		"mappings": {
			"properties": {
				"fileMd5": {"type": "keyword"},
				"chunkId": {"type": "integer"},
				"textContent": {"type": "text", "analyzer": "ik_max_word"},
				"vector": {"type": "dense_vector", "dims": %d, "index": true, "similarity": "cosine"},
				"modelVersion": {"type": "keyword"},
				"userId": {"type": "keyword"},
				"orgTag": {"type": "keyword"},
				"isPublic": {"type": "boolean"}
			}
		}
	}`

	mapping = fmt.Sprintf(mapping, s.cfg.Embedding.Dimension)
	_, err = s.client.CreateIndex(indexName).Body(mapping).Do(context.Background())
	if err != nil {
		return fmt.Errorf("create index failed: %w", err)
	}
	utils.BusinessLog("ES索引 %s 创建成功", indexName)
	return nil
}

// HybridSearchService 混合搜索服务
type HybridSearchService struct {
	esClient       *elastic.Client
	embeddingClient *EmbeddingClient
	fileUploadRepo  *repository.FileUploadRepository
	orgTagCache     *OrgTagCacheService
	cfg             *config.Config
}

func NewHybridSearchService(
	esClient *elastic.Client,
	embeddingClient *EmbeddingClient,
	fileUploadRepo *repository.FileUploadRepository,
	orgTagCache *OrgTagCacheService,
	cfg *config.Config,
) *HybridSearchService {
	return &HybridSearchService{
		esClient:        esClient,
		embeddingClient: embeddingClient,
		fileUploadRepo:  fileUploadRepo,
		orgTagCache:     orgTagCache,
		cfg:             cfg,
	}
}

// Search 公开搜索
func (s *HybridSearchService) Search(query string, topK int) ([]model.SearchResult, error) {
	return s.searchWithFilter(query, topK, nil)
}

// SearchWithPermission 带权限搜索
func (s *HybridSearchService) SearchWithPermission(query string, topK int, userID string) ([]model.SearchResult, error) {
	effectiveTags, err := s.orgTagCache.GetEffectiveTags(userID)
	if err != nil {
		utils.WarnLog("获取有效标签失败: %v", err)
		effectiveTags = []string{}
	}
	return s.searchWithFilter(query, topK, &PermissionFilter{
		UserID:      userID,
		EffectiveTags: effectiveTags,
	})
}

type PermissionFilter struct {
	UserID       string
	EffectiveTags []string
}

func (s *HybridSearchService) searchWithFilter(query string, topK int, filter *PermissionFilter) ([]model.SearchResult, error) {
	indexName := s.cfg.Elasticsearch.IndexName

	// 生成查询向量
	vector, err := s.embeddingClient.EmbedSingle(query)
	if err != nil {
		utils.WarnLog("生成查询向量失败: %v, 回退到纯文本搜索", err)
		return s.textOnlySearch(query, topK, filter)
	}

	// 构建KNN搜索
	knnCandidates := topK * 30
	searchSource := elastic.NewSearchSource().
		Query(s.buildPermissionQuery(filter)).
		Size(knnCandidates)

	// KNN Script Score
	vectorJSON, _ := json.Marshal(vector)
	script := elastic.NewScript(fmt.Sprintf(
		"cosineSimilarity(params.query_vector, 'vector') + 1.0",
	)).Params(map[string]interface{}{"query_vector": json.Number(string(vectorJSON))})

	scriptQuery := elastic.NewScriptScoreQuery(
		elastic.NewMatchAllQuery(), script,
	)

	// BM25文本搜索
	textQuery := elastic.NewMatchQuery("textContent", query)

	// 组合查询
	boolQuery := elastic.NewBoolQuery()
	if filter != nil {
		boolQuery = s.buildPermissionBoolQuery(filter)
	}

	// 使用rescore: 先KNN召回，再BM25重排
	searchResult, err := s.esClient.Search().
		Index(indexName).
		Query(elastic.NewBoolQuery().Should(textQuery).Filter(s.buildPermissionQuery(filter))).
		Rescore(
			elastic.NewRescore().
				RescoreQuery(elastic.NewScriptScoreQuery(elastic.NewMatchAllQuery(), script)).
				QueryWeight(0.2).
				RescoreQueryWeight(1.0).
				ScoreMode("Total"),
		).
		Size(topK).
		Do(context.Background())

	if err != nil {
		return nil, exception.NewInternal(fmt.Sprintf("搜索失败: %v", err))
	}

	return s.parseSearchResults(searchResult), nil
}

func (s *HybridSearchService) textOnlySearch(query string, topK int, filter *PermissionFilter) ([]model.SearchResult, error) {
	indexName := s.cfg.Elasticsearch.IndexName

	textQuery := elastic.NewMatchQuery("textContent", query)
	boolQuery := elastic.NewBoolQuery().Must(textQuery)

	permQuery := s.buildPermissionQuery(filter)
	if permQuery != nil {
		boolQuery.Filter(permQuery)
	}

	searchResult, err := s.esClient.Search().
		Index(indexName).
		Query(boolQuery).
		Size(topK).
		Do(context.Background())

	if err != nil {
		return nil, exception.NewInternal(fmt.Sprintf("文本搜索失败: %v", err))
	}

	return s.parseSearchResults(searchResult), nil
}

func (s *HybridSearchService) buildPermissionQuery(filter *PermissionFilter) elastic.Query {
	if filter == nil {
		// 公开搜索
		return elastic.NewTermQuery("isPublic", true)
	}

	// 私有 + 公开 + 组织标签
	shouldQueries := []elastic.Query{
		elastic.NewTermQuery("userId", filter.UserID),
		elastic.NewTermQuery("isPublic", true),
	}
	if len(filter.EffectiveTags) > 0 {
		shouldQueries = append(shouldQueries, elastic.NewTermsQuery("orgTag", interfaceSlice(filter.EffectiveTags)...))
	}

	return elastic.NewBoolQuery().Should(shouldQueries...).MinimumShouldMatch("1")
}

func (s *HybridSearchService) buildPermissionBoolQuery(filter *PermissionFilter) *elastic.BoolQuery {
	if filter == nil {
		return elastic.NewBoolQuery().Filter(elastic.NewTermQuery("isPublic", true))
	}

	shouldQueries := []elastic.Query{
		elastic.NewTermQuery("userId", filter.UserID),
		elastic.NewTermQuery("isPublic", true),
	}
	if len(filter.EffectiveTags) > 0 {
		shouldQueries = append(shouldQueries, elastic.NewTermsQuery("orgTag", interfaceSlice(filter.EffectiveTags)...))
	}

	return elastic.NewBoolQuery().Should(shouldQueries...).MinimumShouldMatch("1")
}

func (s *HybridSearchService) parseSearchResults(result *elastic.SearchResult) []model.SearchResult {
	var results []model.SearchResult
	for _, hit := range result.Hits.Hits {
		var doc map[string]interface{}
		if err := json.Unmarshal(hit.Source, &doc); err != nil {
			continue
		}

		sr := model.SearchResult{
			Score:    *hit.Score,
			FileMd5:  getStr(doc, "fileMd5"),
			FileName: getStr(doc, "fileName"),
		}
		if v, ok := doc["chunkId"]; ok {
			sr.ChunkID = int(v.(float64))
		}
		if v, ok := doc["textContent"]; ok {
			sr.TextContent = v.(string)
		}
		if v, ok := doc["userId"]; ok {
			sr.UserID = v.(string)
		}
		if v, ok := doc["orgTag"]; ok {
			sr.OrgTag = v.(string)
		}
		if v, ok := doc["isPublic"]; ok {
			sr.IsPublic = v.(bool)
		}

		// 解析文件名
		if sr.FileMd5 != "" {
			fu, err := s.fileUploadRepo.FindByFileMd5(sr.FileMd5)
			if err == nil {
				sr.FileName = fu.FileName
			}
		}

		results = append(results, sr)
	}
	return results
}

func getStr(m map[string]interface{}, key string) string {
	if v, ok := m[key]; ok {
		if s, ok := v.(string); ok {
			return s
		}
	}
	return ""
}

func interfaceSlice(ss []string) []interface{} {
	result := make([]interface{}, len(ss))
	for i, s := range ss {
		result[i] = s
	}
	return result
}
