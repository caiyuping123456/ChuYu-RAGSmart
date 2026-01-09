package com.yizhaoqi.smartpai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 嵌入向量生成客户端
@Component
public class EmbeddingClient {

    @Value("${embedding.api.model}")
    private String modelId;
    
    @Value("${embedding.api.batch-size:100}")
    private int batchSize;

    @Value("${embedding.api.dimension:2048}")
    private int dimension;
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingClient.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public EmbeddingClient(WebClient embeddingWebClient, ObjectMapper objectMapper) {
        this.webClient = embeddingWebClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用通义千问 API 生成向量
     * @param texts 输入文本列表
     * @return 对应的向量列表
     */
    public List<float[]> embed(List<String> texts) {
        try {
            /**
             * 日志打印
             */
            logger.info("开始生成向量，文本数量: {}", texts.size());

            /**
             * 初始化向量list
             * 每个段落是一个向量化
             * 所以list的长度就是texts的长度
             */
            List<float[]> all = new ArrayList<>(texts.size());

            /**
             * 这个代码就是分批次请求大模型进行向量化的循环
             */
            for (int start = 0; start < texts.size(); start += batchSize) {
                int end = Math.min(start + batchSize, texts.size());
                List<String> sub = texts.subList(start, end);
                logger.debug("调用向量 API, 批次: {}-{} (size={})", start, end - 1, sub.size());
                /**
                 * 这个是一次调用大模型进行向量化的方法
                 */
                String response = callApiOnce(sub);
                all.addAll(parseVectors(response));
            }
            /**
             * 全部弄完了后
             * 直接打印日志同时进行输出结果
             */
            logger.info("成功生成向量，总数量: {}", all.size());
            return all;
        } catch (Exception e) {
            logger.error("调用向量化 API 失败: {}", e.getMessage(), e);
            throw new RuntimeException("向量生成失败", e);
        }
    }

    /**
     * 封装请求，进行请求大模型
     * @param batch
     * @return
     */
    private String callApiOnce(List<String> batch) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelId); // 告诉对方：我要用哪款 AI 模型
        requestBody.put("input", batch); // 告诉对方：这是我要转换的几段文本
        requestBody.put("dimension", dimension);  // 直接在根级别设置dimension// 告诉对方：请给我返回 1024 维（或 2048 维）
        requestBody.put("encoding_format", "float");  // 添加编码格式// 告诉对方：数字格式请用标准浮点数

        return webClient.post()
                .uri("/embeddings")// 目标地址：向量接口的门牌号
                .bodyValue(requestBody)// 把刚才准备好的公函放进邮件包
                .retrieve()// 开始接收对方的回应
                .bodyToMono(String.class)// 把对方返回的数据包解析成一串 JSON 字符串
                /**
                 * 这个是重试机制
                 */
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1))
                        .filter(e -> e instanceof WebClientResponseException))
                .block(Duration.ofSeconds(30));
    }

    /**
     * 这个是进行json解析
     *
     * @param response
     * @return
     * @throws Exception
     */
    private List<float[]> parseVectors(String response) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(response);
        JsonNode data = jsonNode.get("data");  // 兼容模式下使用data字段
        if (data == null || !data.isArray()) {
            throw new RuntimeException("API 响应格式错误: data 字段不存在或不是数组");
        }
        
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode embedding = item.get("embedding");
            if (embedding != null && embedding.isArray()) {
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                vectors.add(vector);
            }
        }
        return vectors;
    }
}
