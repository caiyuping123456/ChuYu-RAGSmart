package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.AiAgentMcp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiAgentMcpRepository extends JpaRepository<AiAgentMcp, Long> {
    List<AiAgentMcp> findByAgentIdAndUserIdOrderByIdAsc(Long agentId, Long userId);

    void deleteByAgentIdAndUserId(Long agentId, Long userId);
}
