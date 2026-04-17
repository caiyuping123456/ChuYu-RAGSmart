package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.PythonHttpConfig;
import com.yizhaoqi.smartpai.model.ChatRequest;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

/**
 * @author caiyuping
 * @date 2026/4/21 21:36
 * @description: 这个是向Python发送http请求的
 */
@Service
@Slf4j
public class CallPythonService {

    private final WebClient webClient;
    private final PythonHttpConfig pythonConfig;

    // 构造时初始化 WebClient
    public CallPythonService(WebClient.Builder webClientBuilder, PythonHttpConfig pythonConfig) {
        this.pythonConfig = pythonConfig;
        this.webClient = webClientBuilder
                .baseUrl(pythonConfig.getUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /**
     * 调用 Python FastAPI 并返回响应流
     * Python 返回的是原始文本流，不是标准 SSE 格式
     */
    public Flux<String> callPythonApiStream(ChatRequest requestData) {
        log.info("Calling Python API with request: {}", requestData);
        return webClient.post()
                .uri("/api/v1/agent")
                .header("Authorization", "Bearer " + pythonConfig.getApiKey())
                .accept(MediaType.TEXT_PLAIN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestData)
                .retrieve()
                .bodyToFlux(byte[].class)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .doOnNext(chunk -> log.info("Received chunk from Python: [{}]", chunk))
                .doOnError(error -> log.error("Error calling Python API", error))
                .doOnComplete(() -> log.info("Python API call completed"));
    }
}
