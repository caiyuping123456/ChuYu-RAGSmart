package com.yizhaoqi.smartpai.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.service.TokenCacheService;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${jwt.secret-key}")
    private String secretKeyBase64; // 这里存的是 Base64 编码后的密钥

    private static final long EXPIRATION_TIME = 3600000; // 1 hour (调整为1小时)
    private static final long REFRESH_TOKEN_EXPIRATION_TIME = 604800000; // 7 days (refresh token有效期)
    private static final long REFRESH_THRESHOLD = 300000; // 5分钟：当剩余时间少于5分钟时开始刷新
    private static final long REFRESH_WINDOW = 600000; // 10分钟：token过期后的宽限期
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private TokenCacheService tokenCacheService;

    /**
     * 解析 Base64 密钥，并返回 SecretKey
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT Token（集成Redis缓存）
     */
    public String generateToken(String username) {
        SecretKey key = getSigningKey(); // 解析密钥
        
        // 获取用户信息
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // 生成唯一的tokenId
        /**
         * 通过UUID生成随机Token的ID
         * 用于存redis
         */
        String tokenId = generateTokenId();
        /**
         * 设置redis的过期时间
         */
        long expireTime = System.currentTimeMillis() + EXPIRATION_TIME;
        
        // 创建token内容
        /**
         * 这个标识token中存的信息
         * 存的信息是redis中的tokenid，用户的角色（用于前端判断）
         * 黑油就是用户的id
         * 用户后端快速查找
         */
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenId", tokenId); // 添加tokenId用于Redis缓存
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId().toString()); // 添加用户ID到JWT

        /**
         * 同时将用户的标签添加到token中
         */
        // 添加组织标签信息
        if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
            claims.put("orgTags", user.getOrgTags());
        }

        /**
         * 将主标签添加到token中
         */
        // 添加主组织标签信息
        if (user.getPrimaryOrg() != null && !user.getPrimaryOrg().isEmpty()) {
            claims.put("primaryOrg", user.getPrimaryOrg());
        }

        /**
         * 调用Jwts构建一个token
         * 同时返回前端
         * 这里的本质就是通过signWith(key, SignatureAlgorithm.HS256)
         * 将密钥结合HS256进行加密（这个会存到头部）
         * 解密就不需要这个了
         */
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setExpiration(new Date(expireTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        /**
         * 将token信息存到redis中
         * 这里的信息是tokenid，用户的id,用户的名字
         * 同时设置过期时间
         */
        // 缓存token信息到Redis
        tokenCacheService.cacheToken(tokenId, user.getId().toString(), username, expireTime);

        /**
         * 打印日志
         */
        logger.info("Token generated and cached for user: {}, tokenId: {}", username, tokenId);
        /**
         * 返回token值
         */
        return token;
    }

    /**
     * 验证 JWT Token 是否有效（优先使用Redis缓存）
     */
    public boolean validateToken(String token) {
        try {
            // 首先从JWT中提取tokenId（快速失败）
            String tokenId = extractTokenIdFromToken(token);
            if (tokenId == null) {
                logger.warn("Token does not contain tokenId");
                return false;
            }
            
            // 检查Redis缓存中的token状态
            if (!tokenCacheService.isTokenValid(tokenId)) {
                logger.debug("Token invalid in cache: {}", tokenId);
                return false;
            }
            
            // Redis验证通过，再验证JWT签名（双重验证）
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);

            logger.debug("Token validation successful: {}", tokenId);
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("Token expired: {}", e.getClaims().get("tokenId", String.class));
        } catch (SignatureException e) {
            logger.warn("Invalid token signature");
        } catch (Exception e) {
            logger.error("Error validating token", e);
        }
        return false;
    }

    /**
     * 从 JWT Token 中提取用户名
     */
    public String extractUsernameFromToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            return claims != null ? claims.getSubject() : null;
        } catch (Exception e) {
            logger.error("Error extracting username from token: {}", token, e);
            return null;
        }
    }
    
    /**
     * 从 JWT Token 中提取用户ID
     */
    public String extractUserIdFromToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            return claims != null ? claims.get("userId", String.class) : null;
        } catch (Exception e) {
            logger.error("Error extracting userId from token: {}", token, e);
            return null;
        }
    }
    
    /**
     * 从 JWT Token 中提取用户角色
     */
    public String extractRoleFromToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            return claims != null ? claims.get("role", String.class) : null;
        } catch (Exception e) {
            logger.error("Error extracting role from token: {}", token, e);
            return null;
        }
    }
    
    /**
     * 从 JWT Token 中提取组织标签
     */
    public String extractOrgTagsFromToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            return claims != null ? claims.get("orgTags", String.class) : null;
        } catch (Exception e) {
            logger.error("Error extracting organization tags from token: {}", token, e);
            return null;
        }
    }
    
    /**
     * 从 JWT Token 中提取主组织标签
     */
    public String extractPrimaryOrgFromToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            return claims != null ? claims.get("primaryOrg", String.class) : null;
        } catch (Exception e) {
            logger.error("Error extracting primary organization from token: {}", token, e);
            return null;
        }
    }
    
    /**
     * 检查token是否应该刷新（剩余时间少于阈值）
     * Token的过期时间是一个时间戳，所以需要计算剩余时间
     */
    public boolean shouldRefreshToken(String token) {
        try {
            Claims claims = extractClaims(token);
            if (claims == null) return false;
            
            long expirationTime = claims.getExpiration().getTime();
            long currentTime = System.currentTimeMillis();
            long remainingTime = expirationTime - currentTime;
            
            return remainingTime > 0 && remainingTime < REFRESH_THRESHOLD;
        } catch (Exception e) {
            logger.debug("Cannot check if token should refresh: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查过期token是否仍可刷新（在宽限期内）
     * 宽限期10分钟，也就是过期10分钟内还可以刷新
     */
    public boolean canRefreshExpiredToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            if (claims == null) return false;
            
            long expirationTime = claims.getExpiration().getTime();
            long currentTime = System.currentTimeMillis();
            long expiredTime = currentTime - expirationTime;
            
            return expiredTime > 0 && expiredTime < REFRESH_WINDOW;
        } catch (Exception e) {
            logger.debug("Cannot check if expired token can refresh: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 刷新token（生成新的token）
     */
    public String refreshToken(String oldToken) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(oldToken);
            if (claims == null) return null;
            
            String username = claims.getSubject();
            if (username == null || username.isEmpty()) return null;
            
            // 重新生成token
            String newToken = generateToken(username);
            logger.info("Token refreshed successfully for user: {}", username);
            return newToken;
        } catch (Exception e) {
            logger.error("Error refreshing token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 提取Claims，忽略过期异常
     */
    private Claims extractClaimsIgnoreExpiration(String token) {
        try {
            /**
             * 前端返回的token的头部有一个解密的id,用于指定解密方法
             * 这个不需要我们管
             * JWT自己会根据这个解密方法调用对应的方法
             * 只用传入对应的密钥，和指定需要的到的内容就可以
             */
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            // 忽略过期异常，返回claims
            return e.getClaims();
        } catch (Exception e) {
            logger.debug("Cannot extract claims from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 提取Claims（正常验证）
     */
    private Claims extractClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 生成 Refresh Token（长期有效的刷新令牌，集成Redis缓存）
     */
    public String generateRefreshToken(String username) {
        SecretKey key = getSigningKey();
        /**
         * 先获取用户的信息
         */
        // 获取用户信息
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // 生成唯一的refreshTokenId
        /**
         * 同样使用UUID进行随机tokenid的生成
         */
        String refreshTokenId = generateTokenId();
        /**
         * 这是设置过期时间
         */
        long expireTime = System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION_TIME;
        
        // 创建refreshToken内容（相对简单，只包含基本信息）
        Map<String, Object> claims = new HashMap<>();
        claims.put("refreshTokenId", refreshTokenId); // 添加refreshTokenId
        claims.put("userId", user.getId().toString());
        claims.put("type", "refresh"); // 标识这是一个refresh token

        String refreshToken = Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setExpiration(new Date(expireTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        /**
         * 将token的id,用户id添加到redis
         * 同时设置过期时间
         */
        // 缓存refresh token信息到Redis
        tokenCacheService.cacheRefreshToken(refreshTokenId, user.getId().toString(), null, expireTime);
        
        logger.info("Refresh token generated and cached for user: {}, refreshTokenId: {}", username, refreshTokenId);
        /**
         * 将生成好的信息返回
         */
        return refreshToken;
    }
    
    /**
     * 验证 Refresh Token 是否有效（优先使用Redis缓存）
     */
    public boolean validateRefreshToken(String refreshToken) {
        try {
            // 首先从JWT中提取refreshTokenId
            String refreshTokenId = extractRefreshTokenIdFromToken(refreshToken);
            /**
             * 判断这个tokeId是否存在
             */
            if (refreshTokenId == null) {
                logger.warn("Refresh token does not contain refreshTokenId");
                return false;
            }

            /**
             * 使用这个tokenid判断redis中的是否过期了
             */
            // 检查Redis缓存中的refresh token状态
            if (!tokenCacheService.isRefreshTokenValid(refreshTokenId)) {
                logger.debug("Refresh token invalid in cache: {}", refreshTokenId);
                return false;
            }
            
            // Redis验证通过，再验证JWT签名
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(refreshToken)
                    .getBody();
            
            // 验证是否为refresh token类型
            String tokenType = claims.get("type", String.class);
            if (!"refresh".equals(tokenType)) {
                logger.warn("Token is not a refresh token");
                return false;
            }

            logger.debug("Refresh token validation successful: {}", refreshTokenId);
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("Refresh token expired: {}", e.getClaims().get("refreshTokenId", String.class));
        } catch (SignatureException e) {
            logger.warn("Invalid refresh token signature");
        } catch (Exception e) {
            logger.error("Error validating refresh token", e);
        }
        return false;
    }
    
    /**
     * 从 JWT Token 中提取refreshTokenId
     */
    public String extractRefreshTokenIdFromToken(String refreshToken) {
        try {
            /**
             * 这个Tokenid是放在了一个map中
             * 所以要先得到这个map
             */
            Claims claims = extractClaimsIgnoreExpiration(refreshToken);
            return claims != null ? claims.get("refreshTokenId", String.class) : null;
        } catch (Exception e) {
            logger.debug("Error extracting refreshTokenId from token", e);
            return null;
        }
    }
    
    /**
     * 生成唯一的tokenId
     */
    private String generateTokenId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 从 JWT Token 中提取tokenId
     */
    public String extractTokenIdFromToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            return claims != null ? claims.get("tokenId", String.class) : null;
        } catch (Exception e) {
            logger.debug("Error extracting tokenId from token", e);
            return null;
        }
    }
    
    /**
     * 使token失效（加入Redis黑名单）
     */
    public void invalidateToken(String token) {
        try {
            String tokenId = extractTokenIdFromToken(token);
            if (tokenId != null) {
                Claims claims = extractClaimsIgnoreExpiration(token);
                if (claims != null) {
                    long expireTime = claims.getExpiration().getTime();
                    String userId = claims.get("userId", String.class);
                    
                    // 加入黑名单
                    tokenCacheService.blacklistToken(tokenId, expireTime);
                    // 从缓存中移除
                    tokenCacheService.removeToken(tokenId, userId);
                    
                    logger.info("Token invalidated: {}", tokenId);
                }
            }
        } catch (Exception e) {
            logger.error("Error invalidating token", e);
        }
    }
    
    /**
     * 使用户所有token失效（批量登出）
     */
    public void invalidateAllUserTokens(String userId) {
        try {
            tokenCacheService.removeAllUserTokens(userId);
            logger.info("All tokens invalidated for user: {}", userId);
        } catch (Exception e) {
            logger.error("Error invalidating all user tokens: {}", userId, e);
        }
    }
}
