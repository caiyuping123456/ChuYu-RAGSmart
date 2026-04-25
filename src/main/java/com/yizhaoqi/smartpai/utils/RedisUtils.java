package com.yizhaoqi.smartpai.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author caiyuping
 * @date 2026/4/25 12:40
 * @description: Redis缓存相关的工具类
 */
@Component
@Slf4j
public class RedisUtils {

    private static RedisTemplate<String, Object> redisTemplate;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        RedisUtils.redisTemplate = redisTemplate;
    }

    /**
     * 删除redis中的缓存
     * @param key 键
     */
    public static void deleteRedisOfString(String key) {
        if (key != null) {
            redisTemplate.delete(key);
        }
    }

    /**
     * 添加/更新 redis 缓存
     * @param key 键
     * @param result 值 (支持任意对象，因为用了 Object)
     */
    public static void insertRedisOfString(String key, Object result) {
        redisTemplate.opsForValue().set(key, result, 60, TimeUnit.MINUTES);
    }

    /**
     * 获取缓存
     * @param key 键
     * @return 值
     */
    public static Object getRedisOfString(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 将对象转为 JSON 字符串缓存（与 Python 端格式一致）
     * 使用 StringRedisSerializer 确保存的是纯 JSON 字符串，不带 Java 类型信息
     */
    public static void setJson(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            // 直接用 String 类型写入，绕过 GenericJackson2JsonRedisSerializer 的类型包装
            redisTemplate.execute((connection) -> {
                byte[] keyBytes = redisTemplate.getStringSerializer().serialize(key);
                byte[] valueBytes = redisTemplate.getStringSerializer().serialize(json);
                connection.set(keyBytes, valueBytes);
                // 设置过期时间 60 分钟
                connection.expire(keyBytes, 3600);
                return null;
            }, true);
        } catch (JsonProcessingException e) {
            log.error("Redis setJson error, key={}", key, e);
        }
    }

    /**
     * 获取 JSON 字符串缓存
     */
    public static String getJson(String key) {
        return redisTemplate.execute((connection) -> {
            byte[] keyBytes = redisTemplate.getStringSerializer().serialize(key);
            byte[] valueBytes = connection.get(keyBytes);
            if (valueBytes == null) {
                return null;
            }
            return redisTemplate.getStringSerializer().deserialize(valueBytes);
        }, true);
    }
}