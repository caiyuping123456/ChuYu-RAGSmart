package service

import (
	"bufio"
	"fmt"
	"io"
	"mime/multipart"
	"regexp"
	"strings"

	"goDemo/internal/config"
	"goDemo/internal/middleware/exception"
	"goDemo/internal/model"
	"goDemo/internal/repository"
	"goDemo/internal/utils"
)

// ParseService 文档解析服务
type ParseService struct {
	docVectorRepo *repository.DocumentVectorRepository
	cfg           *config.Config
}

func NewParseService(
	docVectorRepo *repository.DocumentVectorRepository,
	cfg *config.Config,
) *ParseService {
	return &ParseService{
		docVectorRepo: docVectorRepo,
		cfg:           cfg,
	}
}

// ParseAndSave 解析文档并保存分块
func (s *ParseService) ParseAndSave(fileMd5 string, fileHeader *multipart.FileHeader, userID, orgTag string, isPublic bool) error {
	file, err := fileHeader.Open()
	if err != nil {
		return exception.NewInternal("打开文件失败")
	}
	defer file.Close()

	// 读取文件内容
	content, err := io.ReadAll(file)
	if err != nil {
		return exception.NewInternal("读取文件失败")
	}

	text := string(content)

	// 文本分块
	chunks := s.chunkText(text, s.cfg.File.ChunkSize)
	if len(chunks) == 0 {
		return exception.NewBadRequest("文档内容为空")
	}

	// 保存分块到数据库
	var docVectors []model.DocumentVector
	for i, chunk := range chunks {
		dv := model.DocumentVector{
			FileMd5:      fileMd5,
			ChunkID:      i,
			TextContent:  chunk,
			ModelVersion: "",
			UserID:       userID,
			OrgTag:       orgTag,
			IsPublic:     isPublic,
		}
		docVectors = append(docVectors, dv)
	}

	if err := s.docVectorRepo.BatchCreate(docVectors); err != nil {
		return exception.NewInternal(fmt.Sprintf("保存文档分块失败: %v", err))
	}

	utils.FileOperationLog("parse", fileMd5, fmt.Sprintf("chunks=%d", len(chunks)))
	return nil
}

// ParseFromPath 从文件路径解析文档
func (s *ParseService) ParseFromPath(fileMd5, filePath, userID, orgTag string, isPublic bool) error {
	// 这里简化处理，实际可使用unidic或其他Go库解析
	// 从文件路径读取内容并分块
	return fmt.Errorf("从路径解析需要具体实现")
}

// ParseFromReader 从io.Reader解析文档
func (s *ParseService) ParseFromReader(fileMd5 string, reader io.Reader, userID, orgTag string, isPublic bool) error {
	content, err := io.ReadAll(reader)
	if err != nil {
		return exception.NewInternal("读取文件流失败")
	}

	text := string(content)
	chunks := s.chunkText(text, s.cfg.File.ChunkSize)
	if len(chunks) == 0 {
		return exception.NewBadRequest("文档内容为空")
	}

	var docVectors []model.DocumentVector
	for i, chunk := range chunks {
		dv := model.DocumentVector{
			FileMd5:      fileMd5,
			ChunkID:      i,
			TextContent:  chunk,
			UserID:       userID,
			OrgTag:       orgTag,
			IsPublic:     isPublic,
		}
		docVectors = append(docVectors, dv)
	}

	if err := s.docVectorRepo.BatchCreate(docVectors); err != nil {
		return exception.NewInternal(fmt.Sprintf("保存文档分块失败: %v", err))
	}

	utils.FileOperationLog("parse_stream", fileMd5, fmt.Sprintf("chunks=%d", len(chunks)))
	return nil
}

// chunkText 文本分块（模拟Java中的HanLP分块策略）
func (s *ParseService) chunkText(text string, chunkSize int) []string {
	if text == "" {
		return nil
	}

	// 预处理：去除多余空白
	text = strings.TrimSpace(text)
	if len(text) == 0 {
		return nil
	}

	var chunks []string
	scanner := bufio.NewScanner(strings.NewReader(text))
	scanner.Buffer(make([]byte, 1024*1024), 1024*1024)

	var currentChunk strings.Builder
	currentLen := 0

	for scanner.Scan() {
		line := scanner.Text()
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}

		if currentLen+len(line)+1 > chunkSize && currentLen > 0 {
			chunks = append(chunks, currentChunk.String())
			currentChunk.Reset()
			currentLen = 0
		}

		if currentLen > 0 {
			currentChunk.WriteString("\n")
			currentLen++
		}
		currentChunk.WriteString(line)
		currentLen += len(line)

		// 如果单行就超过chunkSize，按句子分割
		if currentLen > chunkSize {
			remaining := currentChunk.String()
			currentChunk.Reset()
			currentLen = 0
			sentenceChunks := s.splitBySentences(remaining, chunkSize)
			for i, sc := range sentenceChunks {
				if i < len(sentenceChunks)-1 {
					chunks = append(chunks, sc)
				} else {
					currentChunk.WriteString(sc)
					currentLen = len(sc)
				}
			}
		}
	}

	if currentLen > 0 {
		chunks = append(chunks, currentChunk.String())
	}

	return chunks
}

// splitBySentences 按句子分割
func (s *ParseService) splitBySentences(text string, chunkSize int) []string {
	// 中文和英文句子结束符
	re := regexp.MustCompile(`[。！？.!?\n]+`)
	indices := re.FindAllStringIndex(text, -1)

	var sentences []string
	lastIdx := 0
	for _, loc := range indices {
		sentences = append(sentences, text[lastIdx:loc[1]])
		lastIdx = loc[1]
	}
	if lastIdx < len(text) {
		sentences = append(sentences, text[lastIdx:])
	}

	var chunks []string
	var current strings.Builder
	currentLen := 0
	for _, sent := range sentences {
		if currentLen+len(sent) > chunkSize && currentLen > 0 {
			chunks = append(chunks, current.String())
			current.Reset()
			currentLen = 0
		}
		current.WriteString(sent)
		currentLen += len(sent)
	}
	if currentLen > 0 {
		chunks = append(chunks, current.String())
	}

	return chunks
}
