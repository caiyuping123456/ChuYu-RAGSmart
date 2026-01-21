package com.yizhaoqi.smartpai.langchain4j;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * 这个是langchain4j配置文件
 */
@Data
@Configuration
public class LangChain4jConfig {
    /**
     * 聊天模型
     */
    /**
     * 对话模型的key
     */
    @Value("${deepseek.api.key}")
    private String chatApiKey;

    /**
     * 对话模型的请求地址
     */
    @Value("${deepseek.api.url}")
    private String ChatBaseUrl;

    /**
     * 对话模型的模型
     */
    @Value("${deepseek.api.model}")
    private String ChatModel;


    /**
     * 嵌入模型
     */
    /**
     * 嵌入模型的key
     */
    @Value("${embedding.api.key}")
    private String EmbeddingApiKey;

    /**
     * 嵌入模型的请求地址
     */
    @Value("${embedding.api.url}")
    private String EmbeddingBaseUrl;

    /**
     * 嵌入模型的模型
     */
    @Value("${embedding.api.model}")
    private String EmbeddingModel;

    /**
     * 最大请求行数
     */
    @Value("${embedding.api.batch-size}")
    private int EmbeddingBatchSize;

    /**
     * 向量维度
     */
    @Value("${embedding.api.dimension}")
    private int EmbeddinDimension;
}
