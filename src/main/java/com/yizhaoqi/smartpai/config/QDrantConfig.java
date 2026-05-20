package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * @author caiyuping
 * @date 2026/5/19 20:30
 * @description: 业务
 */
@Configuration
@Component
@Data
public class QDrantConfig {
    @Value("${qdrant.url}")
    private String url;
    @Value("${qdrant.port}")
    private String port;
    @Value("${qdrant.https}")
    private Boolean https;
    @Value("${qdrant.collection}")
    private String collection;
    @Value("${vl-embedding.api.dimension}")
    private Integer dimension;
    @Value("${qdrant.api_key}")
    private String apiKey;
}
