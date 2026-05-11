package com.yizhaoqi.smartpai.langchain4j.vlembedding;

import com.yizhaoqi.smartpai.langchain4j.LangChain4jConfig;
import com.yizhaoqi.smartpai.langchain4j.image.ImagesClient;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 多模态文件处理工具类
 * PDF按页截图 → 直接VL向量化（或先识图再向量化）
 * 纯图片 → 直接VL向量化（或先识图再向量化）
 * 其他文件走Tika（由调用方处理）
 */
@Component
@Slf4j
public class VLEmbeddingToolUtils {

    @Resource
    private ImagesClient imagesClient;

    @Resource
    private VLEmbeddingModelUtils vlEmbeddingModelUtils;

    @Resource
    private LangChain4jConfig langChain4jConfig;

    private static final String VISION_PROMPT = "你是图片内容提取工具。请提取图片中的所有可读信息：\n" +
            "1. 文字内容：原样输出，保留段落和换行\n" +
            "2. 表格：用Markdown表格格式\n" +
            "3. 图表：提取标题、标签、关键数据\n" +
            "4. 流程图/架构图：描述节点和关系\n" +
            "5. 手写/模糊内容：确定的输出，不确定的用[?]标注\n" +
            "规则：只输出图片中明确存在的内容，不要添加解释、总结、风格描述，不要输出\"这是一张...\"开头，直接输出提取结果。";

    // ==================== PDF 截图直接向量化（推荐，跳过识图） ====================

    /**
     * PDF按页截图 → 直接VL向量化（不经过识图，保留完整图文信息）
     *
     * @param pdfInputStream PDF文件输入流
     * @param dpi            截图分辨率，推荐150
     * @return 每页的向量，按页码顺序
     */
    public List<float[]> embedPdfPagesDirect(InputStream pdfInputStream, float dpi) {
        List<float[]> vectors = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfInputStream)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            log.info("PDF截图直接向量化: 共{}页, dpi={}", totalPages, dpi);

            for (int i = 0; i < totalPages; i++) {
                log.info("正在向量化第{}/{}页", i + 1, totalPages);
                BufferedImage pageImage = renderer.renderImageWithDPI(i, dpi);
                String base64 = imageToBase64(pageImage, "png");
                float[] vector = vlEmbeddingModelUtils.embedImageBase64(base64, langChain4jConfig.getVLDIMENSION());
                vectors.add(vector);
            }
        } catch (Exception e) {
            log.error("PDF截图直接向量化失败", e);
            throw new RuntimeException("PDF截图直接向量化失败", e);
        }
        return vectors;
    }

    /**
     * PDF按页截图直接向量化，默认dpi=150
     */
    public List<float[]> embedPdfPagesDirect(InputStream pdfInputStream) {
        return embedPdfPagesDirect(pdfInputStream, 150);
    }

    // ==================== PDF 截图识图（需要文本描述时用） ====================

    /**
     * PDF按页截图 → 识图 → 文本描述列表
     */
    public List<String> processPdf(InputStream pdfInputStream, float dpi) {
        List<String> pageDescriptions = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfInputStream)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            log.info("PDF截图识图: 共{}页, dpi={}", totalPages, dpi);

            for (int i = 0; i < totalPages; i++) {
                log.info("正在识图第{}/{}页", i + 1, totalPages);
                BufferedImage pageImage = renderer.renderImageWithDPI(i, dpi);
                String base64 = imageToBase64(pageImage, "png");
                String description = recognizeBase64(base64, "image/png");
                pageDescriptions.add(description);
            }
        } catch (Exception e) {
            log.error("PDF截图识图失败", e);
            throw new RuntimeException("PDF截图识图失败", e);
        }
        return pageDescriptions;
    }

    /**
     * PDF截图识图，默认dpi=150
     */
    public List<String> processPdf(InputStream pdfInputStream) {
        return processPdf(pdfInputStream, 150);
    }

    /**
     * PDF截图识图后，对描述文本进行VL向量化
     */
    public List<float[]> embedPdfPages(InputStream pdfInputStream, float dpi) {
        List<String> descriptions = processPdf(pdfInputStream, dpi);
        List<float[]> vectors = new ArrayList<>();
        for (String desc : descriptions) {
            vectors.add(vlEmbeddingModelUtils.embedText(desc));
        }
        return vectors;
    }

    /**
     * PDF截图识图后向量化，默认dpi=150
     */
    public List<float[]> embedPdfPages(InputStream pdfInputStream) {
        return embedPdfPages(pdfInputStream, 150);
    }

    // ==================== 图片处理 ====================

    /**
     * 图片URL → 直接VL向量化（推荐，跳过识图）
     */
    public float[] embedImageDirect(String imageUrl) {
        return vlEmbeddingModelUtils.embedImage(imageUrl);
    }

    /**
     * 图片URL → 识图 → 文本描述
     */
    public String processImageByUrl(String imageUrl) {
        log.info("图片URL识图: url={}", imageUrl);
        UserMessage userMessage = UserMessage.from(
                TextContent.from(VISION_PROMPT),
                ImageContent.from(imageUrl)
        );
        return imagesClient.imagesModel(userMessage);
    }

    /**
     * 图片Base64 → 识图 → 文本描述
     */
    public String processImageByBase64(String base64Image, String mimeType) {
        log.info("图片Base64识图: mimeType={}", mimeType);
        return recognizeBase64(base64Image, mimeType);
    }

    /**
     * 图片流 → 识图 → 文本描述
     */
    public String processImageByStream(InputStream imageInputStream, String mimeType) {
        try {
            BufferedImage image = ImageIO.read(imageInputStream);
            String base64 = imageToBase64(image, mimeTypeToFormat(mimeType));
            return recognizeBase64(base64, mimeType);
        } catch (Exception e) {
            log.error("图片流识图失败", e);
            throw new RuntimeException("图片流识图失败", e);
        }
    }

    // ==================== 文本向量化 ====================

    /**
     * 对文本生成VL向量
     */
    public float[] embedText(String text) {
        return vlEmbeddingModelUtils.embedText(text);
    }

    // ==================== 辅助方法 ====================

    /**
     * 合并多页描述为一个完整文本
     */
    public String mergePageDescriptions(List<String> pageDescriptions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pageDescriptions.size(); i++) {
            if (i > 0) sb.append("\n\n--- 第").append(i + 1).append("页 ---\n\n");
            sb.append(pageDescriptions.get(i));
        }
        return sb.toString();
    }

    private String recognizeBase64(String base64Image, String mimeType) {
        UserMessage userMessage = UserMessage.from(
                TextContent.from(VISION_PROMPT),
                ImageContent.from("data:" + mimeType + ";base64," + base64Image)
        );
        return imagesClient.imagesModel(userMessage);
    }

    private static final int MAX_IMAGE_WIDTH = 800;

    private String imageToBase64(BufferedImage image, String format) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage resized = compressImage(image);
            ImageIO.write(resized, "jpg", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("图片转Base64失败", e);
        }
    }

    /**
     * 压缩图片：等比缩放到最大宽度800px，输出JPG
     */
    private BufferedImage compressImage(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        if (w <= MAX_IMAGE_WIDTH) return original;

        int newW = MAX_IMAGE_WIDTH;
        int newH = (int) ((double) h / w * newW);
        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, newW, newH, null);
        g.dispose();
        return resized;
    }

    private String mimeTypeToFormat(String mimeType) {
        if (mimeType == null) return "png";
        if (mimeType.contains("jpeg") || mimeType.contains("jpg")) return "jpg";
        if (mimeType.contains("png")) return "png";
        return "png";
    }
}
