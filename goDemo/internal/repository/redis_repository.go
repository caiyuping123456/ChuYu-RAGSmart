package repository

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"goDemo/internal/model"

	"github.com/go-redis/redis/v8"
)

// RedisRepository Redis数据访问层
type RedisRepository struct {
	client *redis.Client
}

func NewRedisRepository(client *redis.Client) *RedisRepository {
	return &RedisRepository{client: client}
}

// ===== 对话管理 =====

// GetCurrentConversationID 获取用户当前对话ID
func (r *RedisRepository) GetCurrentConversationID(userID string) (string, error) {
	key := fmt.Sprintf("user:%s:current_conversation", userID)
	val, err := r.client.Get(context.Background(), key).Result()
	if err == redis.Nil {
		return "", nil
	}
	return val, err
}

// SetCurrentConversationID 设置用户当前对话ID
func (r *RedisRepository) SetCurrentConversationID(userID, conversationID string) error {
	key := fmt.Sprintf("user:%s:current_conversation", userID)
	return r.client.Set(context.Background(), key, conversationID, 7*24*time.Hour).Err()
}

// GetConversationHistory 获取对话历史
func (r *RedisRepository) GetConversationHistory(conversationID string) ([]model.Message, error) {
	key := fmt.Sprintf("conversation:%s", conversationID)
	val, err := r.client.Get(context.Background(), key).Result()
	if err == redis.Nil {
		return []model.Message{}, nil
	}
	if err != nil {
		return nil, err
	}

	var messages []model.Message
	if err := json.Unmarshal([]byte(val), &messages); err != nil {
		return nil, err
	}
	return messages, nil
}

// SaveConversationHistory 保存对话历史（7天TTL）
func (r *RedisRepository) SaveConversationHistory(conversationID string, messages []model.Message) error {
	key := fmt.Sprintf("conversation:%s", conversationID)
	data, err := json.Marshal(messages)
	if err != nil {
		return err
	}
	return r.client.Set(context.Background(), key, data, 7*24*time.Hour).Err()
}

// ===== 上传位图 =====

// MarkChunkUploaded 标记分块已上传
func (r *RedisRepository) MarkChunkUploaded(userID, fileMd5 string, chunkIndex int) error {
	key := fmt.Sprintf("upload:%s:%s", userID, fileMd5)
	return r.client.SetBit(context.Background(), key, int64(chunkIndex), 1).Err()
}

// IsChunkUploaded 检查分块是否已上传
func (r *RedisRepository) IsChunkUploaded(userID, fileMd5 string, chunkIndex int) (bool, error) {
	key := fmt.Sprintf("upload:%s:%s", userID, fileMd5)
	val, err := r.client.GetBit(context.Background(), key, int64(chunkIndex)).Result()
	if err != nil {
		return false, err
	}
	return val == 1, nil
}

// GetUploadedChunks 获取已上传的分块列表
func (r *RedisRepository) GetUploadedChunks(userID, fileMd5 string, totalChunks int) ([]int, error) {
	key := fmt.Sprintf("upload:%s:%s", userID, fileMd5)
	var uploaded []int
	for i := 0; i < totalChunks; i++ {
		val, err := r.client.GetBit(context.Background(), key, int64(i)).Result()
		if err != nil {
			return nil, err
		}
		if val == 1 {
			uploaded = append(uploaded, i)
		}
	}
	return uploaded, nil
}

// DeleteUploadBitmap 删除上传位图
func (r *RedisRepository) DeleteUploadBitmap(userID, fileMd5 string) error {
	key := fmt.Sprintf("upload:%s:%s", userID, fileMd5)
	return r.client.Del(context.Background(), key).Err()
}

// ===== Token管理 =====

// CacheToken 缓存Token
func (r *RedisRepository) CacheToken(tokenID, userID string, expire time.Duration) error {
	key := fmt.Sprintf("token:%s", tokenID)
	return r.client.Set(context.Background(), key, userID, expire).Err()
}

// IsTokenBlacklisted 检查Token是否被拉黑
func (r *RedisRepository) IsTokenBlacklisted(tokenID string) (bool, error) {
	key := fmt.Sprintf("token:blacklist:%s", tokenID)
	val, err := r.client.Exists(context.Background(), key).Result()
	if err != nil {
		return false, err
	}
	return val > 0, nil
}

// BlacklistToken 拉黑Token
func (r *RedisRepository) BlacklistToken(tokenID string, expire time.Duration) error {
	key := fmt.Sprintf("token:blacklist:%s", tokenID)
	return r.client.Set(context.Background(), key, "1", expire).Err()
}

// InvalidateAllUserTokens 使某用户所有Token失效
func (r *RedisRepository) InvalidateAllUserTokens(userID string) error {
	key := fmt.Sprintf("user:%s:tokens_version", userID)
	return r.client.Incr(context.Background(), key).Err()
}

// ===== 组织标签缓存 =====

// CacheUserOrgTags 缓存用户组织标签
func (r *RedisRepository) CacheUserOrgTags(userID string, orgTags string, expire time.Duration) error {
	key := fmt.Sprintf("user:%s:org_tags", userID)
	return r.client.Set(context.Background(), key, orgTags, expire).Err()
}

// GetCachedUserOrgTags 获取缓存的用户组织标签
func (r *RedisRepository) GetCachedUserOrgTags(userID string) (string, error) {
	key := fmt.Sprintf("user:%s:org_tags", userID)
	val, err := r.client.Get(context.Background(), key).Result()
	if err == redis.Nil {
		return "", nil
	}
	return val, err
}

// ===== 停止标志 =====

// SetStopFlag 设置停止标志
func (r *RedisRepository) SetStopFlag(userID string) error {
	key := fmt.Sprintf("stop:%s", userID)
	return r.client.Set(context.Background(), key, "1", 5*time.Minute).Err()
}

// IsStopRequested 检查是否请求停止
func (r *RedisRepository) IsStopRequested(userID string) (bool, error) {
	key := fmt.Sprintf("stop:%s", userID)
	val, err := r.client.Exists(context.Background(), key).Result()
	if err != nil {
		return false, err
	}
	return val > 0, nil
}

// ClearStopFlag 清除停止标志
func (r *RedisRepository) ClearStopFlag(userID string) error {
	key := fmt.Sprintf("stop:%s", userID)
	return r.client.Del(context.Background(), key).Err()
}
