package com.yizhaoqi.smartpai.langchain4j.embedding;

import com.yizhaoqi.smartpai.langchain4j.LangChain4jConfig;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class EmbeddingConfig {

    @Resource
    LangChain4jConfig langChain4jConfig;


    /**
     * 嵌入式模型
     * @return
     */
    @Bean
    public EmbeddingModel embeddingModel(){
        log.info("正在注入嵌入式模型");
       return OpenAiEmbeddingModel.builder()
                .apiKey(langChain4jConfig.getEmbeddingApiKey())
                .modelName(langChain4jConfig.getEmbeddingModel())
                .baseUrl(langChain4jConfig.getChatBaseUrl())
                .build();
    }
}
