package com.yizhaoqi.smartpai.langchain4j.image;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import jakarta.annotation.Resource;
import net.coobird.thumbnailator.Thumbnails;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

@SpringBootTest
public class testImageModel {

    @Resource
    private ImagesClient imagesClient;

    @Test
    void testImageRecognition() {
        // 使用一个稳定的、公开可访问的图片URL（例如来自GitHub或维基百科的图片）
        // 以下是一个示例猫咪图片的URL
        String publicImageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4d/Cat_November_2010-1a.jpg/800px-Cat_November_2010-1a.jpg";

        UserMessage userMessage = UserMessage.from(
                TextContent.from("请用中文描述这张图片中的主要内容。"),
                ImageContent.from(publicImageUrl)
        );

        String result = imagesClient.imagesModel(userMessage);
        System.out.println("AI输出：" + result);
    }

    @Test
    void testScreenshot() throws IOException {
        // 你的截图路径
        String imagePath = "D:\\图片\\Screenshots\\屏幕截图 2026-04-10 203843.png";

        // ========== 关键：压缩图片（解决 Token 超限问题）==========
        BufferedImage originalImage = ImageIO.read(new File(imagePath));
        System.out.println("原始尺寸: " + originalImage.getWidth() + " x " + originalImage.getHeight());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Thumbnails.of(originalImage)
                .size(1280, 1280)           // 限制最长边为1280px
                .outputFormat("jpg")         // 转JPG格式
                .outputQuality(0.85)         // 85%质量
                .toOutputStream(baos);

        byte[] compressedBytes = baos.toByteArray();
        String base64Image = Base64.getEncoder().encodeToString(compressedBytes);
        System.out.println("压缩后大小: " + (compressedBytes.length / 1024) + " KB");
        // ===================================================

        // 调用识别（注意：MIME类型改为 image/jpeg）
        UserMessage userMessage = UserMessage.from(
                TextContent.from("说一下这个图"),
                ImageContent.from(base64Image, "image/jpeg")  // 改成 jpeg
        );

        String result = imagesClient.imagesModel(userMessage);
        System.out.println("AI输出：" + result);
    }
}
