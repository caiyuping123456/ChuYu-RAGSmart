package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "ai_agent")
public class AiAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "model_type", nullable = false, length = 10)
    private String modelType;

    @Column(length = 20)
    private String provider = "openai";

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "custom_api_url")
    private String customApiUrl = "https://api.siliconflow.cn";

    @Column(name = "custom_api_key")
    private String customApiKey = "sk-eejpytshnxihqbeedqsgfuixodazjxjxjetbounsqbzzygtr";

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Transient
    private List<AiAgentMcp> mcpServices;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
