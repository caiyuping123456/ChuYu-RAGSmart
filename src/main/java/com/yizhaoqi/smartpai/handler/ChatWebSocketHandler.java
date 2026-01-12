package com.yizhaoqi.smartpai.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.service.ChatHandler;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final ChatHandler chatHandler;
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtUtils jwtUtils;
    
    // 内部指令令牌 - 可以从配置文件读取
    /**
     * 这个静态方法的作用非常简单，但它背后代表的是一套安全防护机制。
     * 简单来说，它的意思是：“把服务器生成的那个‘秘密口令’告诉前端。”
     * 以下是详细的拆解和为什么要这么做的原因：
     * 1. 它是做什么的？
     * 在你的 ChatWebSocketHandler 类中，定义了一个变量： private static final String INTERNAL_CMD_TOKEN = "WSS_STOP_CMD_" + ...;
     * 这是什么：这是一个在后端程序启动时随机生成的、唯一的字符串密钥。
     * 方法作用：getInternalCmdToken() 方法就像是一个窗口，允许外部（通常是通过之前提到的 ChatController 里的 HTTP 接口）获取这个字符串的内容。
     * 2. 为什么要设计这个 Token？（核心原因：防伪造）
     * 由于 WebSocket 链接一旦建立，任何人只要知道你的连接地址，理论上都可以往里面发送数据。
     * 如果后端没有这个 Token 校验，任何人只需要在浏览器控制台输入： socket.send('{"type": "stop"}') 就能让 AI 停止生成。如果你正在做一个付费 AI 接口，别人就可以利用这个漏洞恶意中断你用户的对话。
     * 现在有了这个 Token，流程变成了这样：
     * 后端：启动时生成一个随机 Token（例如：WSS_STOP_CMD_123456）。
     * 前端：想要停止时，先通过 HTTP 接口 调用这个方法拿到这个 Token。
     * 校验：前端发送指令给 WebSocket：{"type": "stop", "_internal_cmd_token": "WSS_STOP_CMD_123456"}。
     * 后端：对比消息里的 Token 和内存里的 INTERNAL_CMD_TOKEN 是否一致。一致才执行停止。
     */
    private static final String INTERNAL_CMD_TOKEN = "WSS_STOP_CMD_" + System.currentTimeMillis() % 1000000;

    public ChatWebSocketHandler(ChatHandler chatHandler, JwtUtils jwtUtils) {
        this.chatHandler = chatHandler;
        this.jwtUtils = jwtUtils;
    }

    /**
     * 这里的执行顺序是：afterConnectionEstablished->handleTextMessage->afterConnectionClosed
     * @param session
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        /**
         * 调用类内部的私有方法。从当前连接的 URL 路径中截取 Token，
         * 并通过 JWT 工具类解密，识别出这个连接属于哪个具体的系统用户（如 username）。
         */
        String userId = extractUserId(session);
        /**
         * 将解析出的 userId 作为 Key，当前的 session 对象作为 Value，存入 ConcurrentHashMap 类型的成员变量 sessions 中。
         * 存入 Map 后，后端其他服务（如异步 AI 处理类）只要知道 userId，就能从这个 Map 里找到对应的 session 并给用户回话。
         */
        sessions.put(userId, session);
        /**
         * 日志打印
         */
        logger.info("WebSocket连接已建立，用户ID: {}，会话ID: {}，URI路径: {}", 
                    userId, session.getId(), session.getUri().getPath());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        /**
         * 同样是获取用户的id
         */
        String userId = extractUserId(session);
        try {
            String payload = message.getPayload();
            /**
             * payload 就是前端发送的原始字符串（可能是普通的一句“你好”，也可能是一个 JSON 字符串）。
             */
            logger.info("接收到消息，用户ID: {}，会话ID: {}，消息长度: {}", 
                       userId, session.getId(), payload.length());

            /**
             * 检查是否是JSON格式的系统指令
              */
            if (payload.trim().startsWith("{")) {
                try {
                    /**
                     * 使用 Jackson 库（objectMapper）将字符串转为 Java 的 Map 对象。
                     */
                    Map<String, Object> jsonMessage = objectMapper.readValue(payload, Map.class);
                    /**
                     * 提取内容：从 JSON 中寻找两个关键字段：
                     * type：动作类型（如 stop）。
                     * _internal_cmd_token：前端传过来的安全令牌。
                     */
                    String messageType = (String) jsonMessage.get("type");
                    String internalToken = (String) jsonMessage.get("_internal_cmd_token");

                    /**
                     * 只有包含正确内部令牌的停止指令才处理
                     */
                    if ("stop".equals(messageType) && INTERNAL_CMD_TOKEN.equals(internalToken)) {
                        // 处理停止指令
                        logger.info("收到有效的停止按钮指令，用户ID: {}，会话ID: {}", userId, session.getId());
                        /**
                         * 调用方法进行指令停止
                         */
                        chatHandler.stopResponse(userId, session);
                        return;
                    }

                    /**
                     * 如果只是简单的聊天消息
                     */
                    // 其他JSON消息当作普通消息处理
                    logger.debug("收到JSON格式的聊天消息，当作普通消息处理");
                } catch (Exception jsonParseError) {
                    // JSON解析失败，当作普通文本消息处理
                    logger.debug("JSON解析失败，当作普通消息处理: {}", jsonParseError.getMessage());
                }
            }
            
            // 普通聊天消息处理（保持向下兼容）
            /**
             * 调用AI进行聊天
             */
            chatHandler.processMessage(userId, payload, session);
            
        } catch (Exception e) {
            /**
             * 异常处理
             */
            logger.error("处理消息出错，用户ID: {}，会话ID: {}，错误: {}", 
                        userId, session.getId(), e.getMessage(), e);
            sendErrorMessage(session, "消息处理失败：" + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        /**
         * 这个就是Socket进行断开连接的方法
         * 清除掉map中存储的key-value
         */
        /**
         * 获取用户的id
         */
        String userId = extractUserId(session);
        /**
         * 清除用户的map值
         */
        sessions.remove(userId);
        /**
         * 日志打印
         */
        logger.info("WebSocket连接已关闭，用户ID: {}，会话ID: {}，状态: {}", 
                    userId, session.getId(), status);
    }

    private String extractUserId(WebSocketSession session) {
        /**
         * 获取请求url中的路径
         */
        String path = session.getUri().getPath();
        String[] segments = path.split("/");
        /**
         * 获取到token
         */
        String jwtToken = segments[segments.length - 1];

        /**
         * 从JWT令牌中提取用户名
          */
        String username = jwtUtils.extractUsernameFromToken(jwtToken);
        /**
         * 判断用户名是不是为空
         */
        if (username == null) {
            logger.warn("无法从JWT令牌中提取用户名，使用令牌作为用户ID: {}", jwtToken);
            /**
             *
             * 为空的话直接返回token
             */
            return jwtToken;
        }

        /**
         * 不为空的话
         * 返回对应的用户名字
         */
        logger.debug("从JWT令牌中提取的用户名: {}", username);
        return username;
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            /**
             * 这个是聊天发生异常
             * 调用后响应前端的方法
             */
            /**
             * 同时存储map中异常信息
             */
            Map<String, String> error = Map.of("error", errorMessage);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
            logger.info("已发送错误消息到会话: {}, 错误: {}", session.getId(), errorMessage);
        } catch (Exception e) {
            logger.error("发送错误消息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取内部指令令牌 - 供前端调用
     */
    /**
     * 这个静态方法的作用非常简单，但它背后代表的是一套安全防护机制。
     * 简单来说，它的意思是：“把服务器生成的那个‘秘密口令’告诉前端。”
     * 以下是详细的拆解和为什么要这么做的原因：
     * 1. 它是做什么的？
     * 在你的 ChatWebSocketHandler 类中，定义了一个变量： private static final String INTERNAL_CMD_TOKEN = "WSS_STOP_CMD_" + ...;
     * 这是什么：这是一个在后端程序启动时随机生成的、唯一的字符串密钥。
     * 方法作用：getInternalCmdToken() 方法就像是一个窗口，允许外部（通常是通过之前提到的 ChatController 里的 HTTP 接口）获取这个字符串的内容。
     * 2. 为什么要设计这个 Token？（核心原因：防伪造）
     * 由于 WebSocket 链接一旦建立，任何人只要知道你的连接地址，理论上都可以往里面发送数据。
     * 如果后端没有这个 Token 校验，任何人只需要在浏览器控制台输入： socket.send('{"type": "stop"}') 就能让 AI 停止生成。如果你正在做一个付费 AI 接口，别人就可以利用这个漏洞恶意中断你用户的对话。
     * 现在有了这个 Token，流程变成了这样：
     * 后端：启动时生成一个随机 Token（例如：WSS_STOP_CMD_123456）。
     * 前端：想要停止时，先通过 HTTP 接口 调用这个方法拿到这个 Token。
     * 校验：前端发送指令给 WebSocket：{"type": "stop", "_internal_cmd_token": "WSS_STOP_CMD_123456"}。
     * 后端：对比消息里的 Token 和内存里的 INTERNAL_CMD_TOKEN 是否一致。一致才执行停止。
     */
    public static String getInternalCmdToken() {
        return INTERNAL_CMD_TOKEN;
    }
} 