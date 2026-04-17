package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.AiAgent;
import com.yizhaoqi.smartpai.model.ChatRequest;
import com.yizhaoqi.smartpai.service.AiAgentService;
import com.yizhaoqi.smartpai.service.CallPythonService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/ai-agents")
@Slf4j
public class AiAgentController {

    private final AiAgentService aiAgentService;
    private final JwtUtils jwtUtils;
    @Resource
    private CallPythonService callPythonService;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public AiAgentController(AiAgentService aiAgentService, JwtUtils jwtUtils) {
        this.aiAgentService = aiAgentService;
        this.jwtUtils = jwtUtils;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader("Authorization") String token) {
        Long userId = extractUserId(token);
        List<AiAgent> agents = aiAgentService.listByUserId(userId);
        return ResponseEntity.ok(Map.of("code", 200, "data", agents));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        Long userId = extractUserId(token);
        AiAgent agent = aiAgentService.getById(id);
        if (!agent.getUserId().equals(userId)) {
            throw new CustomException("No permission", HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(Map.of("code", 200, "data", agent));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader("Authorization") String token, @RequestBody AiAgent agent) {
        Long userId = extractUserId(token);
        agent.setUserId(userId);
        AiAgent created = aiAgentService.create(agent);
        return ResponseEntity.ok(Map.of("code", 200, "message", "创建成功", "data", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@RequestHeader("Authorization") String token,
                                    @PathVariable Long id, @RequestBody AiAgent agent) {
        Long userId = extractUserId(token);
        AiAgent updated = aiAgentService.update(id, agent, userId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功", "data", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        Long userId = extractUserId(token);
        aiAgentService.delete(id, userId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStreamGet(@RequestParam Long agentId,
                                    @RequestParam Long userId,
                                    @RequestParam String question) {
        return createSseEmitter(new ChatRequest(agentId, userId, question));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        return createSseEmitter(request);
    }

    private SseEmitter createSseEmitter(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(60000L); // 60秒超时

        executorService.execute(() -> {
            try {
                log.info("Starting SSE stream for request: {}", request);
                Flux<String> flux = callPythonService.callPythonApiStream(request);

                // 使用 CountDownLatch 等待流完成
                CountDownLatch latch = new CountDownLatch(1);

                flux.subscribe(
                    chunk -> {
                        try {
                            log.info("Sending SSE chunk: [{}]", chunk);
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (IOException e) {
                            log.error("Error sending SSE chunk", e);
                            latch.countDown();
                        }
                    },
                    error -> {
                        log.error("Error in SSE stream", error);
                        emitter.completeWithError(error);
                        latch.countDown();
                    },
                    () -> {
                        log.info("SSE stream completed");
                        emitter.complete();
                        latch.countDown();
                    }
                );

                // 等待流完成
                latch.await(55, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Error creating SSE stream", e);
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(() -> {
            log.warn("SSE emitter timeout");
            emitter.complete();
        });
        emitter.onError(e -> {
            log.error("SSE emitter error", e);
            emitter.complete();
        });

        return emitter;
    }

    private Long extractUserId(String token) {
        String userIdStr = jwtUtils.extractUserIdFromToken(token.replace("Bearer ", ""));
        if (userIdStr == null) {
            throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
        }
        return Long.parseLong(userIdStr);
    }
}
