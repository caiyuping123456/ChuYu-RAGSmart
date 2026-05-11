package com.yizhaoqi.smartpai.langchain4j;

import com.yizhaoqi.smartpai.langchain4j.image.ImagesClient;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * @author caiyuping
 * @date 2026/5/9 9:54
 * @description: 这个是用于配置model的配置类
 */
@Configuration
@Slf4j
public class Langchain4JModelConfig {

    @Resource
    LangChain4jConfig langChain4jConfig;


    /**
     * 这个是识图模型
     * @param ImagesModel
     * @return
     */
    @Bean
    public ImagesClient initImagesClient(ChatLanguageModel ImagesModel){
        return AiServices.builder(ImagesClient.class)
                .chatLanguageModel(ImagesModel)
                .build();
    }

    @Bean
    public ChatLanguageModel initImagesModel() {
        return OpenAiChatModel.builder()
                .apiKey(langChain4jConfig.getIMAGES_KEY())
                .modelName(langChain4jConfig.getIMAGES_MODEL())
                .baseUrl(langChain4jConfig.getIMAGES_URL())
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    /**
     * 阿里视觉向量模型
     * @return
     */
    @Bean
    public EmbeddingModel vlEmbeddingModel(){
        log.info("正在注入视觉向量模型");
        return OpenAiEmbeddingModel.builder()
                .apiKey(langChain4jConfig.getVLKEY())
                .modelName(langChain4jConfig.getVLMODEL())
                .baseUrl(langChain4jConfig.getVLURL())
                .build();
    }

}
