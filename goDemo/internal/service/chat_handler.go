package service

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"goDemo/internal/config"
	"goDemo/internal/middleware/exception"
	"goDemo/internal/model"
	"goDemo/internal/repository"
	"goDemo/internal/utils"
)

// ===== DeepSeek AI 客户端 =====

// ChatMessage 聊天消息
type ChatMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// chatRequest AI聊天请求
type chatRequest struct {
	Model       string        `json:"model"`
	Messages    []ChatMessage `json:"messages"`
	Stream      bool          `json:"stream"`
	Temperature float64       `json:"temperature"`
	MaxTokens   int           `json:"max_tokens"`
	TopP        float64       `json:"top_p"`
}

// streamResponse SSE流式响应
type streamResponse struct {
	Choices []struct {
		Delta struct {
			Content string `json:"content"`
		} `json:"delta"`
		FinishReason *string `json:"finish_reason"`
	} `json:"choices"`
}

// DeepSeekClient DeepSeek AI客户端
type DeepSeekClient struct {
	cfg        *config.Config
	httpClient *http.Client
}

func NewDeepSeekClient(cfg *config.Config) *DeepSeekClient {
	return &DeepSeekClient{
		cfg:        cfg,
		httpClient: &http.Client{Timeout: 5 * time.Minute},
	}
}

// StreamResponse 流式调用DeepSeek API
func (c *DeepSeekClient) StreamResponse(
	ctxMessages []ChatMessage,
	onChunk func(chunk string),
	onError func(err error),
	onComplete func(fullResponse string),
) {
	messages := make([]ChatMessage, 0, len(ctxMessages)+1)
	messages = append(messages, ChatMessage{Role: "system", Content: c.cfg.AI.SystemPrompt})
	messages = append(messages, ctxMessages...)

	reqBody := chatRequest{
		Model:       c.cfg.DeepSeek.Model,
		Messages:    messages,
		Stream:      true,
		Temperature: c.cfg.AI.Temperature,
		MaxTokens:   c.cfg.AI.MaxTokens,
		TopP:        c.cfg.AI.TopP,
	}

	jsonData, err := json.Marshal(reqBody)
	if err != nil {
		onError(fmt.Errorf("序列化请求失败: %w", err))
		return
	}

	req, err := http.NewRequest("POST", c.cfg.DeepSeek.URL+"/chat/completions", bytes.NewReader(jsonData))
	if err != nil {
		onError(fmt.Errorf("创建请求失败: %w", err))
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+c.cfg.DeepSeek.APIKey)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		onError(fmt.Errorf("发送请求失败: %w", err))
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		onError(fmt.Errorf("API错误 %d: %s", resp.StatusCode, string(body)))
		return
	}

	var fullResponse strings.Builder
	scanner := bufio.NewScanner(resp.Body)
	scanner.Buffer(make([]byte, 1024*1024), 1024*1024)

	for scanner.Scan() {
		line := scanner.Text()
		if !strings.HasPrefix(line, "data: ") {
			continue
		}
		data := strings.TrimPrefix(line, "data: ")
		if data == "[DONE]" {
			break
		}

		var sr streamResponse
		if err := json.Unmarshal([]byte(data), &sr); err != nil {
			continue
		}
		for _, choice := range sr.Choices {
			if content := choice.Delta.Content; content != "" {
				fullResponse.WriteString(content)
				onChunk(content)
			}
		}
	}

	if err := scanner.Err(); err != nil {
		utils.ErrorLog("stream scanner error: %v", err)
	}
	onComplete(fullResponse.String())
}

// ===== 对话服务 =====

// ConversationService 对话服务
type ConversationService struct {
	convRepo *repository.ConversationRepository
	userRepo *repository.UserRepository
}

func NewConversationService(
	convRepo *repository.ConversationRepository,
	userRepo *repository.UserRepository,
) *ConversationService {
	return &ConversationService{convRepo: convRepo, userRepo: userRepo}
}

func (s *ConversationService) RecordConversation(userID uint64, question, answer string) error {
	return s.convRepo.Create(&model.Conversation{
		UserID:   userID,
		Question: question,
		Answer:   answer,
	})
}

func (s *ConversationService) GetConversations(userID uint64) ([]model.Conversation, error) {
	return s.convRepo.FindByUserID(userID)
}

func (s *ConversationService) GetAllConversations(adminUsername string, targetUsername string) ([]model.Conversation, error) {
	admin, err := s.userRepo.FindByUsername(adminUsername)
	if err != nil || admin.Role != model.RoleAdmin {
		return nil, exception.NewForbidden("需要管理员权限")
	}
	if targetUsername != "" && targetUsername != "all" {
		user, err := s.userRepo.FindByUsername(targetUsername)
		if err != nil {
			return nil, exception.NewNotFound("目标用户不存在")
		}
		return s.convRepo.FindByUserID(user.ID)
	}
	return s.convRepo.FindAll()
}

// ===== 聊天处理（核心RAG流程）=====

// ChatHandler 聊天处理服务
type ChatHandler struct {
	hybridSearch *HybridSearchService
	deepseek     *DeepSeekClient
	redisRepo    *repository.RedisRepository
	convService  *ConversationService
}

func NewChatHandler(
	hybridSearch *HybridSearchService,
	deepseek *DeepSeekClient,
	redisRepo *repository.RedisRepository,
	convService *ConversationService,
) *ChatHandler {
	return &ChatHandler{
		hybridSearch: hybridSearch,
		deepseek:     deepseek,
		redisRepo:    redisRepo,
		convService:  convService,
	}
}

// ProcessMessage 处理聊天消息
func (h *ChatHandler) ProcessMessage(userID, question string, onChunk func(chunk string)) error {
	// 1. 获取或创建对话ID
	convID, err := h.redisRepo.GetCurrentConversationID(userID)
	if err != nil {
		return err
	}
	if convID == "" {
		convID = fmt.Sprintf("conv_%s", userID)
		_ = h.redisRepo.SetCurrentConversationID(userID, convID)
	}

	// 2. 加载对话历史
	history, err := h.redisRepo.GetConversationHistory(convID)
	if err != nil {
		history = []model.Message{}
	}
	if len(history) > 20 {
		history = history[len(history)-20:]
	}

	// 3. 执行混合搜索
	searchResults, err := h.hybridSearch.SearchWithPermission(question, 5, userID)
	if err != nil {
		searchResults = []model.SearchResult{}
	}

	// 4. 构建上下文
	var ctxBuilder strings.Builder
	for i, r := range searchResults {
		if i >= 5 {
			break
		}
		content := r.TextContent
		if len(content) > 300 {
			content = content[:300] + "..."
		}
		src := r.FileName
		if src == "" {
			src = r.FileMd5
		}
		ctxBuilder.WriteString(fmt.Sprintf("<<REF>>%s#%d<<END>>\n%s\n\n", src, r.ChunkID, content))
	}

	ctxStr := ctxBuilder.String()
	enhancedQ := question
	if ctxStr != "" {
		enhancedQ = fmt.Sprintf("参考资料：\n%s\n\n用户问题：%s", ctxStr, question)
	}

	// 5. 构建消息列表
	chatMsgs := make([]ChatMessage, 0, len(history)+1)
	for _, msg := range history {
		chatMsgs = append(chatMsgs, ChatMessage{Role: msg.Role, Content: msg.Content})
	}
	chatMsgs = append(chatMsgs, ChatMessage{Role: "user", Content: enhancedQ})

	// 6. 调用AI流式响应
	h.deepseek.StreamResponse(
		chatMsgs,
		func(chunk string) {
			if stopped, _ := h.redisRepo.IsStopRequested(userID); stopped {
				return
			}
			onChunk(chunk)
		},
		func(err error) {
			utils.ErrorLog("AI流式响应错误: %v", err)
			onChunk(fmt.Sprintf("\n\n[错误] AI响应失败: %v", err))
		},
		func(fullResponse string) {
			newHistory := append(history,
				model.Message{Role: "user", Content: question},
				model.Message{Role: "assistant", Content: fullResponse},
			)
			_ = h.redisRepo.SaveConversationHistory(convID, newHistory)
			uid, _ := parseUserID(userID)
			_ = h.convService.RecordConversation(uid, question, fullResponse)
			_ = h.redisRepo.ClearStopFlag(userID)
			utils.ChatLog(userID, question, "response_len="+fmt.Sprintf("%d", len(fullResponse)))
		},
	)

	return nil
}

// StopResponse 停止响应
func (h *ChatHandler) StopResponse(userID string) error {
	return h.redisRepo.SetStopFlag(userID)
}
