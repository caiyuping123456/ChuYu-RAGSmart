package com.yizhaoqi.smartpai.langchain4j.VL;

import com.yizhaoqi.smartpai.langchain4j.vlembedding.VLEmbeddingToolUtils;
import com.yizhaoqi.smartpai.service.ImageGetService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;

@SpringBootTest
public class test2 {

    @Resource
    private ImageGetService imageGetService;

    @Resource
    private VLEmbeddingToolUtils vlEmbeddingToolUtils;

    /**
     * 测试图片流识图（直接HTTP调用）
     */
    @Test
    void testImageByStream() throws Exception {
        InputStream is = new FileInputStream("E:\\a\\b.jpg");
        String desc = imageGetService.recognizeByStream(is, "image/jpeg");
        System.out.println("识图结果: " + desc);
    }

    /**
     * 测试图片URL识图（直接HTTP调用）
     */
    @Test
    void testImageByUrl() {
        String desc = imageGetService.recognizeByUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/4/4d/Cat_November_2010-1a.jpg/800px-Cat_November_2010-1a.jpg");
        System.out.println("识图结果: " + desc);
    }

    /**
     * 测试Base64识图（直接HTTP调用）
     */
    @Test
    void testImageByBase64() throws Exception {
        InputStream is = new FileInputStream("E:\\a\\c.png");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        is.transferTo(baos);
        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        System.out.println("base64长度: " + base64.length());
        String desc = imageGetService.recognizeByBase64(base64, "image/png");
        System.out.println("识图结果: " + desc);
        is.close();
    }

    /**
     * 测试VLEmbeddingToolUtils.processPdf()方法
     */
    @Test
    void testProcessPdf() throws Exception {
        InputStream pdfStream = new FileInputStream("D:\\C语言课程设计(1).pdf");
        List<String> pages = vlEmbeddingToolUtils.processPdf(pdfStream, 150);

        System.out.println("共识图 " + pages.size() + " 页");
        for (int i = 0; i < pages.size(); i++) {
            System.out.println("===== 第" + (i + 1) + "页 =====");
            System.out.println(pages.get(i));
        }
    }
}
