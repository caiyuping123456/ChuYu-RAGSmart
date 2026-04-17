package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.AiAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiAgentRepository extends JpaRepository<AiAgent, Long> {
    List<AiAgent> findByUserIdOrderByCreatedAtDesc(Long userId);
}
