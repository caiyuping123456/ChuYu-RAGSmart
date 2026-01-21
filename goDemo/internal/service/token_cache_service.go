package service

import (
	"fmt"
	"strings"
	"time"

	"goDemo/internal/model"
	"goDemo/internal/repository"
	"goDemo/internal/utils"

	"github.com/go-redis/redis/v8"
	"gorm.io/gorm"
)

// TokenCacheService Token缓存服务
type TokenCacheService struct {
	redisRepo *repository.RedisRepository
}

func NewTokenCacheService(redisRepo *repository.RedisRepository) *TokenCacheService {
	return &TokenCacheService{redisRepo: redisRepo}
}

// CacheAccessToken 缓存访问令牌
func (s *TokenCacheService) CacheAccessToken(tokenID, userID string) error {
	return s.redisRepo.CacheToken(tokenID, userID, 1*time.Hour)
}

// CacheRefreshToken 缓存刷新令牌
func (s *TokenCacheService) CacheRefreshToken(tokenID, userID string) error {
	return s.redisRepo.CacheToken(tokenID, userID, 7*24*time.Hour)
}

// IsTokenValid 检查令牌是否有效
func (s *TokenCacheService) IsTokenValid(tokenID string) (bool, error) {
	blacklisted, err := s.redisRepo.IsTokenBlacklisted(tokenID)
	if err != nil {
		return false, err
	}
	return !blacklisted, nil
}

// InvalidateToken 使令牌失效
func (s *TokenCacheService) InvalidateToken(tokenID string) error {
	return s.redisRepo.BlacklistToken(tokenID, 7*24*time.Hour)
}

// InvalidateAllUserTokens 使某用户所有令牌失效
func (s *TokenCacheService) InvalidateAllUserTokens(userID string) error {
	return s.redisRepo.InvalidateAllUserTokens(userID)
}

// OrgTagCacheService 组织标签缓存服务
type OrgTagCacheService struct {
	redisRepo  *repository.RedisRepository
	orgTagRepo *repository.OrganizationTagRepository
	userRepo   *repository.UserRepository
}

func NewOrgTagCacheService(
	redisRepo *repository.RedisRepository,
	orgTagRepo *repository.OrganizationTagRepository,
	userRepo *repository.UserRepository,
) *OrgTagCacheService {
	return &OrgTagCacheService{
		redisRepo:  redisRepo,
		orgTagRepo: orgTagRepo,
		userRepo:   userRepo,
	}
}

// GetEffectiveTags 获取用户的有效标签（包含父级层级）
func (s *OrgTagCacheService) GetEffectiveTags(userID string) ([]string, error) {
	// 先从缓存取
	cached, err := s.redisRepo.GetCachedUserOrgTags(userID)
	if err != nil && err != redis.Nil {
		utils.WarnLog("获取缓存组织标签失败: %v", err)
	}

	var tags []string
	if cached != "" {
		tags = splitTags(cached)
	} else {
		// 从数据库取
		id, err := parseUserID(userID)
		if err != nil {
			return nil, err
		}
		user, err := s.userRepo.FindByID(id)
		if err != nil {
			return nil, err
		}
		tags = splitTags(user.OrgTags)
		// 写入缓存
		_ = s.redisRepo.CacheUserOrgTags(userID, user.OrgTags, 30*time.Minute)
	}

	// 展开父级标签
	effectiveTags := make(map[string]bool)
	for _, tag := range tags {
		effectiveTags[tag] = true
		current := tag
		for {
			orgTag, err := s.orgTagRepo.FindByTagID(current)
			if err != nil || orgTag.ParentTag == "" {
				break
			}
			if !effectiveTags[orgTag.ParentTag] {
				effectiveTags[orgTag.ParentTag] = true
			}
			current = orgTag.ParentTag
		}
	}

	result := make([]string, 0, len(effectiveTags))
	for tag := range effectiveTags {
		result = append(result, tag)
	}
	return result, nil
}

// RefreshCache 刷新缓存
func (s *OrgTagCacheService) RefreshCache(userID, orgTags string) error {
	return s.redisRepo.CacheUserOrgTags(userID, orgTags, 30*time.Minute)
}

// InitializeAdminUser 初始化管理员用户
func InitializeAdminUser(db *gorm.DB, userRepo *repository.UserRepository, orgTagRepo *repository.OrganizationTagRepository) {
	adminUser, err := userRepo.FindByUsername("admin")
	if err == nil && adminUser != nil {
		utils.BusinessLog("管理员用户已存在")
		return
	}
	if err != nil && err != gorm.ErrRecordNotFound {
		utils.ErrorLog("查询管理员用户失败: %v", err)
		return
	}

	// 创建默认组织标签
	if exists, _ := orgTagRepo.ExistsByTagID("DEFAULT"); !exists {
		_ = orgTagRepo.Create(&model.OrganizationTag{TagID: "DEFAULT", Name: "默认组织"})
	}

	// 创建admin组织标签
	if exists, _ := orgTagRepo.ExistsByTagID("admin"); !exists {
		_ = orgTagRepo.Create(&model.OrganizationTag{TagID: "admin", Name: "管理员组织"})
	}

	// 创建管理员
	pwd, _ := utils.EncodePassword("admin123")
	admin := &model.User{
		Username:   "admin",
		Password:   pwd,
		Role:       model.RoleAdmin,
		OrgTags:    "default,admin",
		PrimaryOrg: "default",
	}
	if err := userRepo.Create(admin); err != nil {
		utils.ErrorLog("创建管理员用户失败: %v", err)
		return
	}
	utils.BusinessLog("管理员用户创建成功: admin")
}

func parseUserID(s string) (uint64, error) {
	var id uint64
	_, err := fmt.Sscanf(s, "%d", &id)
	return id, err
}

func splitTags(tags string) []string {
	if tags == "" {
		return []string{}
	}
	parts := strings.Split(tags, ",")
	result := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			result = append(result, p)
		}
	}
	return result
}
