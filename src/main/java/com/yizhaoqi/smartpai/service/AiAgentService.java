package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.AiAgent;
import com.yizhaoqi.smartpai.model.AiAgentMcp;
import com.yizhaoqi.smartpai.repository.AiAgentMcpRepository;
import com.yizhaoqi.smartpai.repository.AiAgentRepository;
import com.yizhaoqi.smartpai.utils.DbEncryptUtil;
import com.yizhaoqi.smartpai.utils.RedisUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAgentService {

    private final AiAgentRepository aiAgentRepository;
    private final AiAgentMcpRepository aiAgentMcpRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Resource
    private DbEncryptUtil dbEncryptUtil;

    /** Redis key 格式：{agentId}:{userId}:  （与 Python 端一致） */
    private String buildRedisKey(Long agentId, Long userId) {
        return agentId + ":" + userId + ":";
    }

    public AiAgentService(
        AiAgentRepository aiAgentRepository,
        AiAgentMcpRepository aiAgentMcpRepository
    ) {
        this.aiAgentRepository = aiAgentRepository;
        this.aiAgentMcpRepository = aiAgentMcpRepository;
    }

    public List<AiAgent> listByUserId(Long userId) {
        List<AiAgent> agents =
            aiAgentRepository.findByUserIdOrderByCreatedAtDesc(userId);
        // MCP 列表不做聚合返回，避免列表接口变重；需要时可通过 detail 获取
        return agents;
    }

    public AiAgent getById(Long id) {
        return aiAgentRepository
            .findById(id)
            .orElseThrow(() ->
                new CustomException("Agent not found", HttpStatus.NOT_FOUND)
            );
    }

    /**
     * 获取 MCP 服务列表
     * @param agentId 代理 ID
     * @param userId 用户 ID
     */
    public List<AiAgentMcp> getMcpServices(Long agentId, Long userId) {
        List<AiAgentMcp> mcpServices = listMcp(agentId, userId);
        mcpServices.stream().forEach(mcp -> {
            // 解密
            mcp.setHeadersJson(dbEncryptUtil.StringDecrypt(mcp.getHeadersJson()));
        });
        return mcpServices;
    }

    private List<AiAgentMcp> listMcp(Long agentId, Long userId) {
        return aiAgentMcpRepository.findByAgentIdAndUserIdOrderByIdAsc(
            agentId,
            userId
        );
    }

    private void replaceMcp(
        Long agentId,
        Long userId,
        List<AiAgentMcp> mcpServices
    ) {
        aiAgentMcpRepository.deleteByAgentIdAndUserId(agentId, userId);
        if (mcpServices == null || mcpServices.isEmpty()) {
            return;
        }
        for (AiAgentMcp mcp : mcpServices) {
            mcp.setId(null);
            mcp.setAgentId(agentId);
            mcp.setUserId(userId);
            if (mcp.getTimeoutMs() == null) {
                mcp.setTimeoutMs(15000);
            }
            String headers = mcp.getHeadersJson();
            if (
                headers == null ||
                headers.isBlank() ||
                "{}".equals(headers.trim())
            ) {
                mcp.setHeadersJson("");
            }
            if (mcp.getTransport() == null || mcp.getTransport().isBlank()) {
                mcp.setTransport("http");
            }
            if (mcp.getUrl() == null || mcp.getUrl().isBlank()) {
                mcp.setUrl("");
            }
            // 这里是对每个MCP的Hearder进行加密
            mcp.setHeadersJson(dbEncryptUtil.StringEncrypt(mcp.getHeadersJson()));
            aiAgentMcpRepository.save(mcp);
        }
    }

    /**
     * 构建 Python 端需要的缓存数据
     */
    private Map<String, String> buildPythonCacheData(AiAgent agent) {
        Map<String, String> cacheData = new LinkedHashMap<>();
        cacheData.put("custom_api_url", agent.getCustomApiUrl());
        cacheData.put("custom_api_key", agent.getCustomApiKey());
        cacheData.put("model_name", agent.getModelName());
        cacheData.put("system_prompt", agent.getSystemPrompt());
        cacheData.put("model_type", agent.getModelType());
        cacheData.put("provider", agent.getProvider());
        return cacheData;
    }

    /**
     * 写入 Redis 缓存（JSON 字符串，与 Python 格式一致）
     */
    private void cacheAgent(AiAgent agent) {
        try {
            String key = buildRedisKey(agent.getId(), agent.getUserId());
            String json = objectMapper.writeValueAsString(
                buildPythonCacheData(agent)
            );
            RedisUtils.setJson(key, json);
        } catch (Exception e) {
            // 缓存失败不影响业务
            throw new RuntimeException("失败，请联系管理");
        }
    }

    /**
     * 删除 Redis 缓存
     */
    private void evictAgent(AiAgent agent) {
        String key = buildRedisKey(agent.getId(), agent.getUserId());
        RedisUtils.deleteRedisOfString(key);
    }

    @Transactional
    public AiAgent create(AiAgent agent) {
        // 加密API_KEY
        agent.setCustomApiKey(dbEncryptUtil.StringEncrypt(agent.getCustomApiKey()));
        AiAgent result = aiAgentRepository.save(agent);
        replaceMcp(result.getId(), result.getUserId(), agent.getMcpServices());
        cacheAgent(result);
        // 返回时带上 MCP 列表
        result.setMcpServices(listMcp(result.getId(), result.getUserId()));
        return result;
    }

    @Transactional
    public AiAgent update(Long id, AiAgent agent, Long userId) {
        AiAgent existing = getById(id);
        if (!existing.getUserId().equals(userId)) {
            throw new CustomException(
                "No permission to update this agent",
                HttpStatus.FORBIDDEN
            );
        }
        existing.setName(agent.getName());
        existing.setDescription(agent.getDescription());
        existing.setSystemPrompt(agent.getSystemPrompt());
        existing.setModelType(agent.getModelType());
        existing.setModelName(agent.getModelName());
        existing.setCustomApiUrl(agent.getCustomApiUrl());
        existing.setCustomApiKey(agent.getCustomApiKey());
        existing.setProvider(agent.getProvider());
        AiAgent result = aiAgentRepository.save(existing);

        replaceMcp(result.getId(), result.getUserId(), agent.getMcpServices());

        cacheAgent(result);
        result.setMcpServices(listMcp(result.getId(), result.getUserId()));
        return result;
    }

    @Transactional
    public void delete(Long id, Long userId) {
        AiAgent existing = getById(id);
        if (!existing.getUserId().equals(userId)) {
            throw new CustomException(
                "No permission to delete this agent",
                HttpStatus.FORBIDDEN
            );
        }
        aiAgentRepository.delete(existing);
        aiAgentMcpRepository.deleteByAgentIdAndUserId(id, userId);
        evictAgent(existing);
    }
}
