package utils

import (
	"encoding/base64"
	"fmt"
	"time"

	"goDemo/internal/config"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

// JWTClaims JWT声明
type JWTClaims struct {
	TokenID    string `json:"tokenId"`
	Role       string `json:"role"`
	UserID     string `json:"userId"`
	OrgTags    string `json:"orgTags"`
	PrimaryOrg string `json:"primaryOrg"`
	jwt.RegisteredClaims
}

// GenerateAccessToken 生成访问令牌
func GenerateAccessToken(userID, role, orgTags, primaryOrg string) (string, string, error) {
	tokenID := uuid.New().String()
	cfg := config.AppConfig.JWT

	claims := JWTClaims{
		TokenID:    tokenID,
		Role:       role,
		UserID:     userID,
		OrgTags:    orgTags,
		PrimaryOrg: primaryOrg,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(time.Duration(cfg.AccessExpire) * time.Second)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, err := token.SignedString(getSigningKey())
	if err != nil {
		return "", "", err
	}
	return signed, tokenID, nil
}

// GenerateRefreshToken 生成刷新令牌
func GenerateRefreshToken(userID, role, orgTags, primaryOrg string) (string, string, error) {
	tokenID := uuid.New().String()
	cfg := config.AppConfig.JWT

	claims := JWTClaims{
		TokenID:    tokenID,
		Role:       role,
		UserID:     userID,
		OrgTags:    orgTags,
		PrimaryOrg: primaryOrg,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(time.Duration(cfg.RefreshExpire) * time.Second)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, err := token.SignedString(getSigningKey())
	if err != nil {
		return "", "", err
	}
	return signed, tokenID, nil
}

// ParseToken 解析JWT令牌
func ParseToken(tokenStr string) (*JWTClaims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &JWTClaims{}, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return getSigningKey(), nil
	})
	if err != nil {
		return nil, err
	}
	if claims, ok := token.Claims.(*JWTClaims); ok && token.Valid {
		return claims, nil
	}
	return nil, fmt.Errorf("invalid token")
}

// IsTokenExpiringSoon 检查令牌是否即将过期（5分钟内）
func IsTokenExpiringSoon(claims *JWTClaims) bool {
	if claims.ExpiresAt == nil {
		return false
	}
	return time.Until(claims.ExpiresAt.Time) < 5*time.Minute
}

// IsTokenExpiredButInGrace 检查令牌是否过期但在宽限期内（10分钟内）
func IsTokenExpiredButInGrace(claims *JWTClaims) bool {
	if claims.ExpiresAt == nil {
		return false
	}
	now := time.Now()
	expiry := claims.ExpiresAt.Time
	return now.After(expiry) && now.Sub(expiry) < 10*time.Minute
}

func getSigningKey() []byte {
	decoded, err := base64.StdEncoding.DecodeString(config.AppConfig.JWT.Secret)
	if err != nil {
		return []byte(config.AppConfig.JWT.Secret)
	}
	return decoded
}
