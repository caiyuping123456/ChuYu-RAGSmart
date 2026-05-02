package com.yizhaoqi.smartpai.langchain4j.VL;

import com.yizhaoqi.smartpai.langchain4j.vlembedding.VLEmbeddingToolUtils;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

@SpringBootTest
public class test {

    @Resource
    private VLEmbeddingToolUtils vlTool;

    /**
     * 测试PDF按页截图直接向量化（推荐路径，跳过识图）
     */
    @Test
    void testEmbedPdfDirect() throws Exception {
        InputStream pdfStream = new FileInputStream("D:\\test.pdf");
        List<float[]> vectors = vlTool.embedPdfPagesDirect(pdfStream);
        System.out.println("PDF页数: " + vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            System.out.println("第" + (i + 1) + "页向量维度: " + vectors.get(i).length);
        }
    }

    /**
     * 测试PDF按页截图识图 → 文本描述
     */
    @Test
    void testProcessPdf() throws Exception {
        InputStream pdfStream = new FileInputStream("D:\\test.pdf");
        List<String> pages = vlTool.processPdf(pdfStream);
        System.out.println("PDF页数: " + pages.size());
        for (int i = 0; i < pages.size(); i++) {
            System.out.println("===== 第" + (i + 1) + "页 =====");
            System.out.println(pages.get(i));
        }
    }

    /**
     * 测试PDF截图识图后向量化
     */
    @Test
    void testEmbedPdfWithDescription() throws Exception {
        InputStream pdfStream = new FileInputStream("D:\\test.pdf");
        List<float[]> vectors = vlTool.embedPdfPages(pdfStream);
        System.out.println("PDF页数: " + vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            System.out.println("第" + (i + 1) + "页向量维度: " + vectors.get(i).length);
        }
    }

    /**
     * 测试PDF识图 + 合并文本
     */
    @Test
    void testMergePdfDescriptions() throws Exception {
        InputStream pdfStream = new FileInputStream("D:\\test.pdf");
        List<String> pages = vlTool.processPdf(pdfStream);
        String fullText = vlTool.mergePageDescriptions(pages);
        System.out.println("合并后文本长度: " + fullText.length());
        System.out.println(fullText);
    }

    /**
     * 测试图片URL直接向量化
     */
    @Test
    void testEmbedImageByUrl() {
        float[] vector = vlTool.embedImageDirect("https://upload.wikimedia.org/wikipedia/commons/thumb/4/4d/Cat_November_2010-1a.jpg/800px-Cat_November_2010-1a.jpg");
        System.out.println("图片向量维度: " + vector.length);
        System.out.println("前5维: " + vector[0] + ", " + vector[1] + ", " + vector[2] + ", " + vector[3] + ", " + vector[4]);
    }

    /**
     * 测试图片URL识图
     */
    @Test
    void testProcessImageByUrl() {
        String desc = vlTool.processImageByUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/4/4d/Cat_November_2010-1a.jpg/800px-Cat_November_2010-1a.jpg");
        System.out.println("图片描述: " + desc);
    }

    /**
     * 测试文本VL向量化
     */
    @Test
    void testEmbedText() {
        float[] vector = vlTool.embedText("这是一段测试文本，用于验证VL文本向量化功能");
        System.out.println("文本向量维度: " + vector.length);
        System.out.println("前5维: " + vector[0] + ", " + vector[1] + ", " + vector[2] + ", " + vector[3] + ", " + vector[4]);
    }
}
