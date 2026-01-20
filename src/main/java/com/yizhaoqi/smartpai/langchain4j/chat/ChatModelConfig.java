package com.yizhaoqi.smartpai.langchain4j.chat;

import com.yizhaoqi.smartpai.langchain4j.LangChain4jConfig;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatModelConfig {

    @Resource
    LangChain4jConfig langChain4jConfig;

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(){
        return OpenAiStreamingChatModel.builder()
                .apiKey(langChain4jConfig.getChatApiKey())
                .modelName(langChain4jConfig.getChatModel())
                .baseUrl(langChain4jConfig.getChatBaseUrl())
                .build();
    }

    @Bean
    public ChatClient assistant(){
        return AiServices.create(ChatClient.class, streamingChatLanguageModel());
    }

}
