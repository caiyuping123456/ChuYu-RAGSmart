package com.yizhaoqi.smartpai.client;

import com.yizhaoqi.smartpai.config.QDrantConfig;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author caiyuping
 * @date 2026/5/19 20:50
 * @description: 业务
 */
@Configuration
public class QDrantClientBean {

    @Resource
    private QDrantConfig qDrantConfig;

    @Bean
    public QdrantClient qdrantClient(){
        return new QdrantClient(
                QdrantGrpcClient.newBuilder(qDrantConfig.getUrl(),
                        Integer.parseInt(qDrantConfig.getPort()),
                        qDrantConfig.getHttps()).build());

    }
}
