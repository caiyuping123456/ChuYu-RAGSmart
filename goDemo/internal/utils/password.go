package utils

import (
	"golang.org/x/crypto/bcrypt"
)

// EncodePassword 使用BCrypt加密密码
func EncodePassword(rawPassword string) (string, error) {
	bytes, err := bcrypt.GenerateFromPassword([]byte(rawPassword), bcrypt.DefaultCost)
	if err != nil {
		return "", err
	}
	return string(bytes), nil
}

// MatchesPassword 验证密码是否匹配
func MatchesPassword(rawPassword, encodedPassword string) bool {
	err := bcrypt.CompareHashAndPassword([]byte(encodedPassword), []byte(rawPassword))
	return err == nil
}
