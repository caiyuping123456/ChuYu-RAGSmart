package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author caiyuping
 * @date 2026/4/21 21:37
 * @description: 业务
 */
@Component
@ConfigurationProperties
@Data
public class PythonHttpConfig {
    @Value("${python.api.base-url}")
    String url;
    @Value("${python.api.timeout}")
    Long timeOut;
    @Value("${python.api.api-key}")
    String apiKey;
}
