package com.yizhaoqi.smartpai.langchain4j.chat;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class ChatUtils {
    @Resource
    ChatClient chatClient;

    public void streamResponse(String userMessage,
                               String context,
                               List<Map<String, String>> history,
                               Consumer<String> onChunk,
                               Consumer<Throwable> onError) {
        System.out.println("content"+context);
        chatClient.chat(userMessage,context,toList(history))
                .onPartialResponse(onChunk)
                .onError(onError)
                .start();
    }

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
