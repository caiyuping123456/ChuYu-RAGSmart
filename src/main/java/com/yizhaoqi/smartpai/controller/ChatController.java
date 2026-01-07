package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.handler.ChatWebSocketHandler;
import com.yizhaoqi.smartpai.service.ChatHandler;
import com.yizhaoqi.smartpai.utils.LogUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController extends TextWebSocketHandler {

    private final ChatHandler chatHandler;

    public ChatController(ChatHandler chatHandler) {
        this.chatHandler = chatHandler;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        /**
         * 这里是获取客户端Socket传过来的消息体
         * 这个SpringSocket中的TextWebSocketHandler的方法
         */
        String userMessage = message.getPayload();
        /**
         * 这个是获取当前对话的Id用来当用户的Id
         */
        String userId = session.getId(); // Use session ID as userId for simplicity

        /**
         * 同样，这个是一个性能监控的方法，传入的是WebSocket——chat操作
         * 用于计时
         */
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("WEBSOCKET_CHAT");
        try {
            /**
             * 这里是打印日志
             */
            LogUtils.logChat(userId, session.getId(), "USER_MESSAGE", userMessage.length());
            LogUtils.logBusiness("WEBSOCKET_CHAT", userId, "处理WebSocket聊天消息: messageLength=%d", userMessage.length());

            /**
             * 这里是根据用户Id 用户发送的消息进行调用大模型回复
             */
            chatHandler.processMessage(userId, userMessage, session);
            
            LogUtils.logUserOperation(userId, "WEBSOCKET_CHAT", "message_processing", "SUCCESS");
            monitor.end("WebSocket消息处理成功");
        } catch (Exception e) {
            LogUtils.logBusinessError("WEBSOCKET_CHAT", userId, "WebSocket消息处理失败", e);
            monitor.end("WebSocket消息处理失败: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 获取WebSocket停止指令Token
     */
    @GetMapping("/websocket-token")
    public ResponseEntity<?> getWebSocketToken() {
        try {
            String cmdToken = ChatWebSocketHandler.getInternalCmdToken();
            
            // 检查token是否有效
            if (cmdToken == null || cmdToken.trim().isEmpty()) {
                return ResponseEntity.status(500).body(Map.of(
                    "code", 500,
                    "message", "Token生成失败",
                    "data", null
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "获取WebSocket停止指令Token成功",
                "data", Map.of("cmdToken", cmdToken)
            ));
            
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_WEBSOCKET_TOKEN", "system", "获取WebSocket Token失败", e);
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "服务器内部错误：" + e.getMessage(),
                "data", null
            ));
        }
    }
}
