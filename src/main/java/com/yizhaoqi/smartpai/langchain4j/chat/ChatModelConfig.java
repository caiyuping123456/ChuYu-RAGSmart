package com.yizhaoqi.smartpai.langchain4j.chat;

import com.yizhaoqi.smartpai.langchain4j.LangChain4jConfig;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ChatModelConfig {

    @Resource
    LangChain4jConfig langChain4jConfig;

    /**
     * 流式对话
     * @return
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(){
        log.info("正在注入流式对话模型");
        return OpenAiStreamingChatModel.builder()
                .apiKey(langChain4jConfig.getChatApiKey())
                .modelName(langChain4jConfig.getChatModel())
                .baseUrl(langChain4jConfig.getChatBaseUrl())
                .build();
    }

    /**
     * 定义的对话模型
     * @return
     */
    @Bean
    public ChatClient assistant(){
        log.info("正在注入对话模型");
        return AiServices.create(ChatClient.class, streamingChatLanguageModel());
    }

}
