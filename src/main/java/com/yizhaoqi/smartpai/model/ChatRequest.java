package com.yizhaoqi.smartpai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI Agent 聊天请求
 * 前端只需要发送 agentId、userId 和用户问题
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatRequest {

    /**
     * 智能体 ID
     */
    private Long agentId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户问题
     */
    private String question;
}
