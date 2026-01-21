package service

import (
	"fmt"
	"strings"

	"goDemo/internal/middleware/exception"
	"goDemo/internal/model"
	"goDemo/internal/repository"
	"goDemo/internal/utils"

	"gorm.io/gorm"
)

// UserService 用户服务
type UserService struct {
	userRepo    *repository.UserRepository
	orgTagRepo  *repository.OrganizationTagRepository
	redisRepo   *repository.RedisRepository
}

func NewUserService(
	userRepo *repository.UserRepository,
	orgTagRepo *repository.OrganizationTagRepository,
	redisRepo *repository.RedisRepository,
) *UserService {
	return &UserService{
		userRepo:   userRepo,
		orgTagRepo: orgTagRepo,
		redisRepo:  redisRepo,
	}
}

// RegisterUser 用户注册
func (s *UserService) RegisterUser(username, password string) (*model.User, error) {
	// 检查用户名是否已存在
	existing, err := s.userRepo.FindByUsername(username)
	if err == nil && existing != nil {
		return nil, exception.NewBadRequest("用户名已存在")
	}
	if err != nil && err != gorm.ErrRecordNotFound {
		return nil, exception.NewInternal("查询用户失败")
	}

	// 确保DEFAULT组织标签存在
	if err := s.ensureDefaultOrgTag(); err != nil {
		return nil, err
	}

	// 加密密码
	encodedPwd, err := utils.EncodePassword(password)
	if err != nil {
		return nil, exception.NewInternal("密码加密失败")
	}

	// 创建用户
	user := &model.User{
		Username:   username,
		Password:   encodedPwd,
		Role:       model.RoleUser,
		OrgTags:    "DEFAULT",
		PrimaryOrg: "DEFAULT",
	}

	if err := s.userRepo.Create(user); err != nil {
		return nil, exception.NewInternal("创建用户失败")
	}

	// 创建用户私有组织标签
	privateTag := &model.OrganizationTag{
		TagID:     fmt.Sprintf("PRIVATE_%s", username),
		Name:      fmt.Sprintf("%s的私有空间", username),
		ParentTag: "DEFAULT",
		CreatedBy: user.ID,
	}
	if err := s.orgTagRepo.Create(privateTag); err != nil {
		// 非致命错误，继续
		utils.ErrorLog("创建私有组织标签失败: %v", err)
	}

	// 将私有标签添加到用户
	user.OrgTags = fmt.Sprintf("DEFAULT,PRIVATE_%s", username)
	if err := s.userRepo.Update(user); err != nil {
		utils.ErrorLog("更新用户组织标签失败: %v", err)
	}

	return user, nil
}

// AuthenticateUser 用户认证
func (s *UserService) AuthenticateUser(username, password string) (*model.User, error) {
	user, err := s.userRepo.FindByUsername(username)
	if err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, exception.NewUnauthorized("用户名或密码错误")
		}
		return nil, exception.NewInternal("查询用户失败")
	}

	if !utils.MatchesPassword(password, user.Password) {
		return nil, exception.NewUnauthorized("用户名或密码错误")
	}

	return user, nil
}

// GetUserByID 通过ID获取用户
func (s *UserService) GetUserByID(id uint64) (*model.User, error) {
	user, err := s.userRepo.FindByID(id)
	if err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, exception.NewNotFound("用户不存在")
		}
		return nil, exception.NewInternal("查询用户失败")
	}
	return user, nil
}

// GetUserByUsername 通过用户名获取用户
func (s *UserService) GetUserByUsername(username string) (*model.User, error) {
	user, err := s.userRepo.FindByUsername(username)
	if err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, exception.NewNotFound("用户不存在")
		}
		return nil, exception.NewInternal("查询用户失败")
	}
	return user, nil
}

// GetUserOrgTags 获取用户组织标签
func (s *UserService) GetUserOrgTags(userID string) ([]string, error) {
	// 先从缓存取
	cached, err := s.redisRepo.GetCachedUserOrgTags(userID)
	if err == nil && cached != "" {
		return strings.Split(cached, ","), nil
	}

	id, err := parseUint(userID)
	if err != nil {
		return nil, exception.NewBadRequest("无效的用户ID")
	}
	user, err := s.userRepo.FindByID(id)
	if err != nil {
		return nil, exception.NewNotFound("用户不存在")
	}

	tags := strings.Split(user.OrgTags, ",")
	// 缓存
	_ = s.redisRepo.CacheUserOrgTags(userID, user.OrgTags, 0)
	return tags, nil
}

// SetUserPrimaryOrg 设置用户主组织
func (s *UserService) SetUserPrimaryOrg(userID, primaryOrg string) error {
	id, err := parseUint(userID)
	if err != nil {
		return exception.NewBadRequest("无效的用户ID")
	}
	user, err := s.userRepo.FindByID(id)
	if err != nil {
		return exception.NewNotFound("用户不存在")
	}

	// 验证primaryOrg在用户的orgTags中
	if !containsTag(user.OrgTags, primaryOrg) {
		return exception.NewBadRequest("该组织标签不在用户的组织列表中")
	}

	return s.userRepo.UpdatePrimaryOrg(id, primaryOrg)
}

// GetAllUsers 获取所有用户
func (s *UserService) GetAllUsers() ([]model.User, error) {
	users, err := s.userRepo.FindAll()
	if err != nil {
		return nil, exception.NewInternal("查询用户列表失败")
	}
	// 清除密码
	for i := range users {
		users[i].Password = ""
	}
	return users, nil
}

// GetUsersPaginated 分页获取用户
func (s *UserService) GetUsersPaginated(keyword, orgTag, status string, page, size int) ([]model.User, int64, error) {
	users, total, err := s.userRepo.FindAllPaginated(keyword, orgTag, status, page, size)
	if err != nil {
		return nil, 0, exception.NewInternal("查询用户列表失败")
	}
	for i := range users {
		users[i].Password = ""
	}
	return users, total, nil
}

// CreateAdminUser 创建管理员用户
func (s *UserService) CreateAdminUser(username, password string, creatorID uint64) (*model.User, error) {
	existing, err := s.userRepo.FindByUsername(username)
	if err == nil && existing != nil {
		return nil, exception.NewBadRequest("用户名已存在")
	}

	encodedPwd, err := utils.EncodePassword(password)
	if err != nil {
		return nil, exception.NewInternal("密码加密失败")
	}

	user := &model.User{
		Username:   username,
		Password:   encodedPwd,
		Role:       model.RoleAdmin,
		OrgTags:    "DEFAULT,admin",
		PrimaryOrg: "DEFAULT",
	}

	if err := s.userRepo.Create(user); err != nil {
		return nil, exception.NewInternal("创建管理员失败")
	}

	return user, nil
}

// AssignOrgTags 分配组织标签给用户
func (s *UserService) AssignOrgTags(userID uint64, tags []string) error {
	user, err := s.userRepo.FindByID(userID)
	if err != nil {
		return exception.NewNotFound("用户不存在")
	}

	// 保留用户的私有标签
	existingTags := strings.Split(user.OrgTags, ",")
	privateTags := []string{}
	for _, tag := range existingTags {
		if strings.HasPrefix(tag, "PRIVATE_") {
			privateTags = append(privateTags, tag)
		}
	}

	allTags := append(privateTags, tags...)
	tagStr := strings.Join(allTags, ",")

	if err := s.userRepo.UpdateOrgTags(userID, tagStr); err != nil {
		return exception.NewInternal("更新组织标签失败")
	}

	// 清除缓存
	_ = s.redisRepo.CacheUserOrgTags(fmt.Sprintf("%d", userID), tagStr, 0)

	return nil
}

// ensureDefaultOrgTag 确保默认组织标签存在
func (s *UserService) ensureDefaultOrgTag() error {
	exists, err := s.orgTagRepo.ExistsByTagID("DEFAULT")
	if err != nil {
		return err
	}
	if !exists {
		defaultTag := &model.OrganizationTag{
			TagID: "DEFAULT",
			Name:  "默认组织",
			CreatedBy: 1, // 默认系统用户
		}
		if err := s.orgTagRepo.Create(defaultTag); err != nil {
			return err
		}
	}
	return nil
}

func containsTag(orgTags, tag string) bool {
	tags := strings.Split(orgTags, ",")
	for _, t := range tags {
		if t == tag {
			return true
		}
	}
	return false
}

func parseUint(s string) (uint64, error) {
	var id uint64
	_, err := fmt.Sscanf(s, "%d", &id)
	return id, err
}
