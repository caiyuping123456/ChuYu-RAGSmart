package com.yizhaoqi.smartpai.langchain4j.chat;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ChatUtils {
    @Resource
    ChatClient chatClient;

    /**
     * 流式调用方法
     * 闯入的是一个Consumer类
     * @param userMessage
     * @param context
     * @param history
     * @param onChunk
     * @param onError
     */
    public void streamResponse(String userMessage,
                               String context,
                               List<Map<String, String>> history,
                               Consumer<String> onChunk,
                               Consumer<Throwable> onError) {
        log.info("langchain4j的流式响应");
        /**
         * 这个是langchain4j的流式响应
         */
        chatClient.chat(userMessage,context,toList(history))
                .onPartialResponse(onChunk)
                .onError(onError)
                .start();
        log.info("langchain4j正在流式调用");
    }

    /**
     * 对参数格式进行处理
     * @param history
     * @return
     */
    private List<ChatMessage>  toList(List<Map<String, String>> history){
        if (history == null) return new ArrayList<>();
        // 1. 转换历史记录 (Map -> ChatMessage)
        return history.stream().map(m -> {
            String role = m.get("role").toLowerCase();
            String content = m.get("content");
            if (role.contains("user")) return UserMessage.from(content);
            if (role.contains("assistant") || role.contains("ai")) return AiMessage.from(content);
            return SystemMessage.from(content);
        }).collect(Collectors.toList());
    }

    /**
     * 这个是测试方法
     * @param userMessage
     * @param context
     * @param history
     * @param onChunk
     * @param onError
     * @throws InterruptedException
     */
    public void Test(String userMessage,
                     String context,
                     List<Map<String, String>> history,
                     Consumer<String> onChunk,
                     Consumer<Throwable> onError) throws InterruptedException {
        chatClient.chat(userMessage,context,toList(history))
                .onPartialResponse((String partialResponse) -> System.out.println(partialResponse))
                .onError((Throwable error) -> error.printStackTrace())
                .start();
        Thread.sleep(30000);
    }
}
