package com.yizhaoqi.smartpai.langchain4j.embedding;

import com.yizhaoqi.smartpai.langchain4j.LangChain4jConfig;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    @Resource
    LangChain4jConfig langChain4jConfig;


    @Bean
    public EmbeddingModel embeddingModel(){
       return OpenAiEmbeddingModel.builder()
                .apiKey(langChain4jConfig.getEmbeddingApiKey())
                .modelName(langChain4jConfig.getEmbeddingModel())
                .baseUrl(langChain4jConfig.getChatBaseUrl())
                .build();
    }
}
