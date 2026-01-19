package com.yizhaoqi.smartpai.client;

import com.yizhaoqi.smartpai.langchain4j.chat.ChatUtils;
import jakarta.annotation.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.yizhaoqi.smartpai.config.AiProperties;

@Service
public class DeepSeekClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final AiProperties aiProperties;
    private static final Logger logger = LoggerFactory.getLogger(DeepSeekClient.class);

    @Resource
    ChatUtils chatUtils;
    
    public DeepSeekClient(@Value("${deepseek.api.url}") String apiUrl,
                         @Value("${deepseek.api.key}") String apiKey,
                         @Value("${deepseek.api.model}") String model,
                         AiProperties aiProperties) {
        WebClient.Builder builder = WebClient.builder().baseUrl(apiUrl);
        
        // 只有当 API key 不为空时才添加 Authorization header
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        
        this.webClient = builder.build();
        this.apiKey = apiKey;
        this.model = model;
        this.aiProperties = aiProperties;
    }

    /**
     * 这段代码是整个 RAG 系统中 “负责拨打电话” 的核心组件。它使用了 Java 的 WebClient（Spring WebFlux 框架的一部分）来发起一个响应式（Reactive） 的 HTTP 请求。
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

        /**
         * sequenceDiagram
         *     participant A as 代码 A (你/业务层)
         *     participant B as 代码 B (WebClient/送奶工)
         *     participant D as DeepSeek (API)
         *
         *     Note over A,B: 1. 只有这一次主动调用
         *     A->>B: streamResponse(..., 回调函数F)
         *
         *     B->>D: 建立网络连接 (HTTP Request)
         *
         *     Note over B,D: 2. 此后全是自动触发
         *
         *     D-->>B: 返回字符 "你"
         *     B-->>A: 执行回调函数F("你")  <-- B通知A
         *
         *     D-->>B: 返回字符 "好"
         *     B-->>A: 执行回调函数F("好")  <-- B又通知A
         *
         *     D-->>B: 返回字符 "呀"
         *     B-->>A: 执行回调函数F("呀")  <-- B又又通知A
         *
         *     D-->>B: [结束信号]
         *     B-->>A: 执行结束逻辑
         */
//        Map<String, Object> request = buildRequest(userMessage, context, history);
//        System.out.println("content"+context);
//        webClient.post()
//                .uri("/chat/completions")
//                .contentType(MediaType.APPLICATION_JSON)
//                .bodyValue(request)
//                .retrieve()
//                .bodyToFlux(String.class)// 开启流式
//                .subscribe(
//                    chunk -> processChunk(chunk, onChunk),
//                    onError
//                );
        logger.info("开始流式请求：用户消息={}, 上下文长度={}, 历史数={}",
                userMessage,
                context != null ? context.length() : 0,
                history != null ? history.size() : 0);

        // 直接调用 ChatUtils，它内部会处理 List<Map> 到 List<ChatMessage> 的转换
        chatUtils.streamResponse(userMessage, context, history, onChunk, onError);
    }

    /**
     * 在调用 AI 之前，它负责把所有的原材料（问题、资料、历史记录）和控制参数（温度、长度限制）装进一个箱子（Map）里，准备发给 DeepSeek 服务器。
     *
     * 这个 Map 最终会被 Spring 的 WebClient 自动转换成 JSON 格式的 HTTP 请求体。
     * @param userMessage
     * @param context
     * @param history
     * @return
     */
    private Map<String, Object> buildRequest(String userMessage, 
                                           String context,
                                           List<Map<String, String>> history) {
        logger.info("构建请求，用户消息：{}，上下文长度：{}，历史消息数：{}", 
                   userMessage, 
                   context != null ? context.length() : 0, 
                   history != null ? history.size() : 0);
        
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("model", model);
        request.put("messages", buildMessages(userMessage, context, history));
        request.put("stream", true);
        // 生成参数
        AiProperties.Generation gen = aiProperties.getGeneration();
        if (gen.getTemperature() != null) {
            request.put("temperature", gen.getTemperature());
        }
        if (gen.getTopP() != null) {
            request.put("top_p", gen.getTopP());
        }
        if (gen.getMaxTokens() != null) {
            request.put("max_tokens", gen.getMaxTokens());
        }
        return request;
    }

    /**
     * 这段代码是 Prompt Engineering（提示词工程） 的核心实现部分。
     *
     * 它的作用是把所有的碎片信息（规则、搜索到的资料、以前的聊天记录、当前的问题）按照大模型能够理解的**“三明治结构”**组装成一个有序的列表。
     * @param userMessage
     * @param context
     * @param history
     * @return
     */
    private List<Map<String, String>> buildMessages(String userMessage,
                                                  String context,
                                                  List<Map<String, String>> history) {
        List<Map<String, String>> messages = new ArrayList<>();

        AiProperties.Prompt promptCfg = aiProperties.getPrompt();

        // 1. 构建统一的 system 指令（规则 + 参考信息）
        StringBuilder sysBuilder = new StringBuilder();
        // A. 注入人设规则 (Rules)
        // 例如："你是一个专业的企业知识库助手，请只根据参考资料回答问题..."
        String rules = promptCfg.getRules();
        if (rules != null) {
            sysBuilder.append(rules).append("\n\n");
        }

        String refStart = promptCfg.getRefStart() != null ? promptCfg.getRefStart() : "<<REF>>";
        String refEnd = promptCfg.getRefEnd() != null ? promptCfg.getRefEnd() : "<<END>>";
        sysBuilder.append(refStart).append("\n");

        if (context != null && !context.isEmpty()) {
            sysBuilder.append(context);
        } else {
            String noResult = promptCfg.getNoResultText() != null ? promptCfg.getNoResultText() : "（本轮无检索结果）";
            sysBuilder.append(noResult).append("\n");
        }

        sysBuilder.append(refEnd);

        String systemContent = sysBuilder.toString();
        messages.add(Map.of(
            "role", "system",
            "content", systemContent
        ));
        logger.debug("添加了系统消息，长度: {}", systemContent.length());

        // 2. 追加历史消息（若有）
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }

        // 3. 当前用户问题
        messages.add(Map.of(
            "role", "user",
            "content", userMessage
        ));

        return messages;
    }
    
    private void processChunk(String chunk, Consumer<String> onChunk) {
        try {
            // 检查是否是结束标记
            if ("[DONE]".equals(chunk)) {
                logger.debug("对话结束");
                return;
            }
            
            // 直接解析 JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(chunk);
            String content = node.path("choices")
                               .path(0)
                               .path("delta")
                               .path("content")
                               .asText("");
            
            if (!content.isEmpty()) {
                onChunk.accept(content);
            }
        } catch (Exception e) {
            logger.error("处理数据块时出错: {}", e.getMessage(), e);
        }
    }
} 