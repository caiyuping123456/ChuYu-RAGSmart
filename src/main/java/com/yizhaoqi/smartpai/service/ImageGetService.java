package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.langchain4j.LangChain4jConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 识图服务 - 直接HTTP调用视觉模型API，绕过LangChain4j
 * 和Python的requests.post逻辑完全一致
 */
@Service
@Slf4j
public class ImageGetService {

    @Resource
    private LangChain4jConfig langChain4jConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 通过base64图片数据调用识图模型
     *
     * @param base64Image base64编码的图片数据（不含data:image/...;base64,前缀）
     * @param mimeType    图片MIME类型，如 image/jpeg
     * @return 识图结果文本
     */
    public String recognizeByBase64(String base64Image, String mimeType) {
        String dataUrl = "data:" + mimeType + ";base64," + base64Image;
        return callVisionApi(dataUrl);
    }

    /**
     * 通过图片URL调用识图模型
     *
     * @param imageUrl 图片的URL地址
     * @return 识图结果文本
     */
    public String recognizeByUrl(String imageUrl) {
        return callVisionApi(imageUrl);
    }

    /**
     * 通过图片流调用识图模型
     *
     * @param imageInputStream 图片输入流
     * @param mimeType         图片MIME类型，如 image/jpeg
     * @return 识图结果文本
     */
    public String recognizeByStream(InputStream imageInputStream, String mimeType) {
        try {
            String base64 = streamToBase64(imageInputStream);
            return recognizeByBase64(base64, mimeType);
        } catch (Exception e) {
            log.error("图片流识图失败", e);
            throw new RuntimeException("图片流识图失败", e);
        }
    }

    /**
     * 统一调用视觉API，和Python的requests.post逻辑完全一致
     */
    private String callVisionApi(String imageUrl) {
        try {
            Map<String, Object> payload = Map.of(
                    "model", langChain4jConfig.getIMAGES_MODEL(),
                    "messages", List.of(
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "image_url",
                                            "image_url", Map.of("url", imageUrl)),
                                    Map.of("type", "text",
                                            "text", "你是图片内容提取工具。请按以下规则提取图片中的所有信息：\n" +
                                                    "\n" +
                                                    "1. 如果图片包含文字，请原样输出所有文字内容，保留段落和换行\n" +
                                                    "2. 如果图片包含表格，请先用一两句话总结表格的核心信息，然后用Markdown表格格式详细输出表格内容\n" +
                                                    "3. 如果图片包含图表/流程图，请提取标题、标签、关键数据和关系\n" +
                                                    "4. 手写或模糊内容：确定的直接输出，不确定的用[?]标注\n" +
                                                    "\n" +
                                                    "规则：只输出图片中明确存在的内容，不要添加解释或总结，不要输出\"这是一张...\"开头，直接输出提取结果。")
                            ))
                    )
            );

            String requestBody = objectMapper.writeValueAsString(payload);
            String apiBase = langChain4jConfig.getIMAGES_URL();
            String fullUrl = apiBase.endsWith("/")
                    ? apiBase + "chat/completions"
                    : apiBase + "/chat/completions";

            URL url = new URL(fullUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + langChain4jConfig.getIMAGES_KEY());
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(300000);

            // 发送请求体
            connection.getOutputStream().write(requestBody.getBytes("UTF-8"));

            // 读取响应
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String response = readStream(connection.getInputStream());
                JsonNode root = objectMapper.readTree(response);
                return root.path("choices").path(0).path("message").path("content").asText();
            } else {
                String errorResponse = readStream(connection.getErrorStream());
                log.error("识图API调用失败, code={}, response={}", responseCode, errorResponse);
                throw new RuntimeException("识图API调用失败: " + errorResponse);
            }
        } catch (Exception e) {
            log.error("识图API调用异常", e);
            throw new RuntimeException("识图API调用异常", e);
        }
    }

    private String streamToBase64(InputStream inputStream) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        inputStream.transferTo(baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private String readStream(InputStream inputStream) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        inputStream.transferTo(baos);
        return baos.toString("UTF-8");
    }
}