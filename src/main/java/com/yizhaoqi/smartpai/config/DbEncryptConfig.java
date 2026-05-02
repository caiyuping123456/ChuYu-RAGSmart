package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author caiyuping
 * @date 2026/5/8 14:23
 * @description: 业务
 */
@ConfigurationProperties
@Data
@Component
public class DbEncryptConfig {

    // AES的密钥
    @Value("${dbencrypt.aes-key}")
    private String AES_KEY;

    // AES的偏移量
    @Value("${dbencrypt.aes-iv}")
    private String AES_IV;
}
