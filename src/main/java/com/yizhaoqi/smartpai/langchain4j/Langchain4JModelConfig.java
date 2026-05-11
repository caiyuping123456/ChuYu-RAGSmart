package com.yizhaoqi.smartpai.langchain4j;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author caiyuping
 * @date 2026/5/9 9:54
 * @description: LangChain4j模型配置类
 * 识图模型已迁移到 ImageGetService（直接HTTP调用，不经过LangChain4j）
 */
@Configuration
@Slf4j
public class Langchain4JModelConfig {

    @Resource
    LangChain4jConfig langChain4jConfig;

    /**
     * 注入VL视觉向量模型（Qwen3-VL-Embedding-8B）
     * 用于PDF截图/图片的VL向量化，维度4096
     */
    @Bean
    public EmbeddingModel vlEmbeddingModel() {
        log.info("正在注入视觉向量模型");
        return OpenAiEmbeddingModel.builder()
                .apiKey(langChain4jConfig.getVLKEY())
                .modelName(langChain4jConfig.getVLMODEL())
                .baseUrl(langChain4jConfig.getVLURL())
                .build();
    }

}
