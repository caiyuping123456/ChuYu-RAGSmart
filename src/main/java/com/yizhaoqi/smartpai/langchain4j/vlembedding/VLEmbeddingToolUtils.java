package com.yizhaoqi.smartpai.langchain4j.vlembedding;

import com.yizhaoqi.smartpai.langchain4j.LangChain4jConfig;
import com.yizhaoqi.smartpai.service.ImageGetService;
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
    private ImageGetService imageGetService;

    @Resource
    private VLEmbeddingModelUtils vlEmbeddingModelUtils;

    @Resource
    private LangChain4jConfig langChain4jConfig;

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
                String base64 = imageToBase64(pageImage, "jpg");
                String description = imageGetService.recognizeByBase64(base64, "image/jpeg");
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
        return imageGetService.recognizeByUrl(imageUrl);
    }

    /**
     * 图片Base64 → 识图 → 文本描述
     */
    public String processImageByBase64(String base64Image, String mimeType) {
        log.info("图片Base64识图: mimeType={}", mimeType);
        return imageGetService.recognizeByBase64(base64Image, mimeType);
    }

    /**
     * 图片流 → 识图 → 文本描述
     */
    public String processImageByStream(InputStream imageInputStream, String mimeType) {
        try {
            return imageGetService.recognizeByStream(imageInputStream, mimeType);
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
     * 渲染PDF所有页为BufferedImage列表（只渲染一次，识图和向量化复用）
     */
    public List<BufferedImage> renderPdfPages(InputStream pdfInputStream, float dpi) {
        List<BufferedImage> images = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfInputStream)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            log.info("PDF渲染: 共{}页, dpi={}", totalPages, dpi);
            for (int i = 0; i < totalPages; i++) {
                images.add(renderer.renderImageWithDPI(i, dpi));
            }
        } catch (Exception e) {
            throw new RuntimeException("PDF渲染失败", e);
        }
        return images;
    }

    /**
     * 对预渲染的图片列表做VL向量化（避免重复渲染PDF）
     */
    public List<float[]> embedImagesDirect(List<BufferedImage> pageImages) {
        List<float[]> vectors = new ArrayList<>();
        for (int i = 0; i < pageImages.size(); i++) {
            log.info("VL向量化第{}/{}页", i + 1, pageImages.size());
            String base64 = imageToBase64(pageImages.get(i), "jpg");
            float[] vector = vlEmbeddingModelUtils.embedImageBase64(base64, langChain4jConfig.getVLDIMENSION());
            vectors.add(vector);
        }
        return vectors;
    }

    /**
     * 对预渲染的图片列表做识图（避免重复渲染PDF）
     */
    public List<String> recognizeImages(List<BufferedImage> pageImages) {
        List<String> descriptions = new ArrayList<>();
        for (int i = 0; i < pageImages.size(); i++) {
            log.info("识图第{}/{}页", i + 1, pageImages.size());
            String base64 = imageToBase64(pageImages.get(i), "jpg");
            String description = imageGetService.recognizeByBase64(base64, "image/jpeg");
            descriptions.add(description);
        }
        return descriptions;
    }

    /**
     * 图片流转Base64再做VL向量化
     */
    public float[] embedImageFromStream(InputStream imageInputStream) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            imageInputStream.transferTo(baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return vlEmbeddingModelUtils.embedImageBase64(base64, langChain4jConfig.getVLDIMENSION());
        } catch (Exception e) {
            throw new RuntimeException("图片流VL向量化失败", e);
        }
    }

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

    private static final int MAX_IMAGE_WIDTH = 800;

    private String imageToBase64(BufferedImage image, String format) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage resized = compressImage(image);
            ImageIO.write(resized, format, baos);
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
        int newW = Math.min(w, MAX_IMAGE_WIDTH);
        int newH = (int) ((double) h / w * newW);
        // 始终转为TYPE_INT_RGB，去掉透明通道
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
