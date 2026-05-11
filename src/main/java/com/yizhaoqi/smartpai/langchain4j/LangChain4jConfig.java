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

    /**
     * 识图模型的URL
     */
    @Value("${images.api.url}")
    private String IMAGES_URL;

    /**
     * 识图模型的API_KEY
     */
    @Value("${images.api.key}")
    private String IMAGES_KEY;

    /**
     * 识图模型的MODEL
     */
    @Value("${images.api.model}")
    private String IMAGES_MODEL;

    /**
     * 阿里识图向量模型URL
     */
    @Value("${vl-embedding.api.url}")
    private String VLURL;

    /**
     * 阿里识图向量模型KEY
     */
    @Value("${vl-embedding.api.key}")
    private String VLKEY;

    /**
     * 阿里识图向量模型MODEL
     */
    @Value("${vl-embedding.api.model}")
    private String VLMODEL;

    /**
     * 阿里识图向量模型批量次数
     */
    @Value("${vl-embedding.api.batch-size}")
    private int VLBATCH_SIZE;

    /**
     * 阿里识图向量模型向量维度
     */
    @Value("${vl-embedding.api.dimension}")
    private int VLDIMENSION;
}
