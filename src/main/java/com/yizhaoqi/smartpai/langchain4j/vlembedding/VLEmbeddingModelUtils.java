package com.yizhaoqi.smartpai.langchain4j.vlembedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.langchain4j.LangChain4jConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 视觉向量模型工具类
 * 支持 Qwen3-VL-Embedding-8B，可对图片URL和文本生成向量
 */
@Component
@Slf4j
public class VLEmbeddingModelUtils {

    @Resource
    LangChain4jConfig langChain4jConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 对单张图片URL生成向量
     */
    public float[] embedImage(String imageUrl) {
        return embedImage(imageUrl, langChain4jConfig.getVLDIMENSION());
    }

    /**
     * 对单张图片URL生成向量，可指定维度
     */
    public float[] embedImage(String imageUrl, int dimension) {
        log.info("VL Embedding: 对图片URL生成向量, url={}", imageUrl);
        java.util.Map<String, Object> input = java.util.Map.of("image", imageUrl);
        return embedSingle(input, dimension);
    }

    /**
     * 对Base64图片生成向量（用于PDF截图等内存中的图片）
     */
    public float[] embedImageBase64(String base64Image, int dimension) {
        log.info("VL Embedding: 对Base64图片生成向量");
        java.util.Map<String, Object> input = java.util.Map.of("image", "data:image/png;base64," + base64Image);
        return embedSingle(input, dimension);
    }

    /**
     * 对单条文本生成向量
     */
    public float[] embedText(String text) {
        return embedText(text, langChain4jConfig.getVLDIMENSION());
    }

    /**
     * 对单条文本生成向量，可指定维度
     */
    public float[] embedText(String text, int dimension) {
        log.info("VL Embedding: 对文本生成向量, length={}", text.length());
        String requestBody = buildTextRequest(text, dimension);
        return callEmbeddingApi(requestBody);
    }

    /**
     * 批量：对多张图片URL生成向量
     */
    public List<float[]> embedImages(List<String> imageUrls) {
        return embedImages(imageUrls, langChain4jConfig.getVLDIMENSION());
    }

    /**
     * 批量：对多张图片URL生成向量，可指定维度
     */
    public List<float[]> embedImages(List<String> imageUrls, int dimension) {
        log.info("VL Embedding: 批量图片向量化, count={}", imageUrls.size());
        int batchSize = langChain4jConfig.getVLBATCH_SIZE();
        List<float[]> allVectors = new ArrayList<>();

        for (int i = 0; i < imageUrls.size(); i += batchSize) {
            List<String> batch = imageUrls.subList(i, Math.min(i + batchSize, imageUrls.size()));
            List<Object> inputItems = new ArrayList<>();
            for (String url : batch) {
                inputItems.add(java.util.Map.of("image", url));
            }
            String requestBody = buildBatchRequest(inputItems, dimension);
            List<float[]> batchVectors = callEmbeddingApiBatch(requestBody);
            allVectors.addAll(batchVectors);
        }

        return allVectors;
    }

    /**
     * 混合输入：文本 + 图片URL 列表，生成向量
     * input 格式: ["text1", {"image": "url1"}, {"text": "text2"}, ...]
     */
    public List<float[]> embedMixed(List<Object> inputs) {
        return embedMixed(inputs, langChain4jConfig.getVLDIMENSION());
    }

    /**
     * 混合输入，可指定维度
     */
    public List<float[]> embedMixed(List<Object> inputs, int dimension) {
        log.info("VL Embedding: 混合输入向量化, count={}", inputs.size());
        int batchSize = langChain4jConfig.getVLBATCH_SIZE();
        List<float[]> allVectors = new ArrayList<>();

        for (int i = 0; i < inputs.size(); i += batchSize) {
            List<Object> batch = inputs.subList(i, Math.min(i + batchSize, inputs.size()));
            String requestBody = buildBatchRequest(batch, dimension);
            List<float[]> batchVectors = callEmbeddingApiBatch(requestBody);
            allVectors.addAll(batchVectors);
        }

        return allVectors;
    }

    // ==================== 通用调用 ====================

    private float[] embedSingle(java.util.Map<String, Object> input, int dimension) {
        try {
            java.util.Map<String, Object> request = new java.util.LinkedHashMap<>();
            request.put("model", langChain4jConfig.getVLMODEL());
            request.put("input", input);
            if (dimension > 0) request.put("dimensions", dimension);
            String requestBody = objectMapper.writeValueAsString(request);
            return callEmbeddingApi(requestBody);
        } catch (Exception e) {
            throw new RuntimeException("VL Embedding单条请求失败", e);
        }
    }

    // ==================== 请求体构建 ====================

    private String buildTextRequest(String text, int dimension) {
        try {
            java.util.Map<String, Object> request = new java.util.LinkedHashMap<>();
            request.put("model", langChain4jConfig.getVLMODEL());
            request.put("input", text);
            if (dimension > 0) request.put("dimensions", dimension);
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new RuntimeException("构建文本向量化请求失败", e);
        }
    }

    private String buildBatchRequest(List<Object> inputItems, int dimension) {
        try {
            java.util.Map<String, Object> request = new java.util.LinkedHashMap<>();
            request.put("model", langChain4jConfig.getVLMODEL());
            request.put("input", inputItems);
            if (dimension > 0) request.put("dimensions", dimension);
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new RuntimeException("构建批量向量化请求失败", e);
        }
    }

    // ==================== API 调用 ====================

    private float[] callEmbeddingApi(String requestBody) {
        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(langChain4jConfig.getVLURL())
                    .defaultHeader("Authorization", "Bearer " + langChain4jConfig.getVLKEY())
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            String response = webClient.post()
                    .uri("/embeddings")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseSingleEmbedding(response);
        } catch (Exception e) {
            log.error("VL Embedding API调用失败", e);
            throw new RuntimeException("VL Embedding API调用失败", e);
        }
    }

    private List<float[]> callEmbeddingApiBatch(String requestBody) {
        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(langChain4jConfig.getVLURL())
                    .defaultHeader("Authorization", "Bearer " + langChain4jConfig.getVLKEY())
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            String response = webClient.post()
                    .uri("/embeddings")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseBatchEmbeddings(response);
        } catch (Exception e) {
            log.error("VL Embedding 批量API调用失败", e);
            throw new RuntimeException("VL Embedding 批量API调用失败", e);
        }
    }

    // ==================== 响应解析 ====================

    private float[] parseSingleEmbedding(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode embeddingNode = root.path("data").path(0).path("embedding");
            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = (float) embeddingNode.get(i).asDouble();
            }
            log.debug("VL Embedding: 解析向量成功, dimension={}", vector.length);
            return vector;
        } catch (Exception e) {
            throw new RuntimeException("解析VL Embedding响应失败: " + response, e);
        }
    }

    private List<float[]> parseBatchEmbeddings(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataArray = root.path("data");
            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : dataArray) {
                JsonNode embeddingNode = item.path("embedding");
                float[] vector = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = (float) embeddingNode.get(i).asDouble();
                }
                vectors.add(vector);
            }
            log.debug("VL Embedding: 批量解析向量成功, count={}", vectors.size());
            return vectors;
        } catch (Exception e) {
            throw new RuntimeException("解析VL Embedding批量响应失败: " + response, e);
        }
    }
}
