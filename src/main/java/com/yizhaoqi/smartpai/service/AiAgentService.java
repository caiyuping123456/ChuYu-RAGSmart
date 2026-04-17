package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.AiAgent;
import com.yizhaoqi.smartpai.repository.AiAgentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AiAgentService {

    private final AiAgentRepository aiAgentRepository;

    public AiAgentService(AiAgentRepository aiAgentRepository) {
        this.aiAgentRepository = aiAgentRepository;
    }

    public List<AiAgent> listByUserId(Long userId) {
        return aiAgentRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public AiAgent getById(Long id) {
        return aiAgentRepository.findById(id)
                .orElseThrow(() -> new CustomException("Agent not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public AiAgent create(AiAgent agent) {
        return aiAgentRepository.save(agent);
    }

    @Transactional
    public AiAgent update(Long id, AiAgent agent, Long userId) {
        AiAgent existing = getById(id);
        if (!existing.getUserId().equals(userId)) {
            throw new CustomException("No permission to update this agent", HttpStatus.FORBIDDEN);
        }
        existing.setName(agent.getName());
        existing.setDescription(agent.getDescription());
        existing.setSystemPrompt(agent.getSystemPrompt());
        existing.setModelType(agent.getModelType());
        existing.setModelName(agent.getModelName());
        existing.setCustomApiUrl(agent.getCustomApiUrl());
        existing.setCustomApiKey(agent.getCustomApiKey());
        return aiAgentRepository.save(existing);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        AiAgent existing = getById(id);
        if (!existing.getUserId().equals(userId)) {
            throw new CustomException("No permission to delete this agent", HttpStatus.FORBIDDEN);
        }
        aiAgentRepository.delete(existing);
    }
}
