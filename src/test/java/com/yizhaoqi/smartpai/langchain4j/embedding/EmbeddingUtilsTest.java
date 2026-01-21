package com.yizhaoqi.smartpai.langchain4j.embedding;

import com.yizhaoqi.smartpai.SmartPaiApplication;
import com.yizhaoqi.smartpai.client.EmbeddingClient;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SmartPaiApplication.class)
class EmbeddingUtilsTest {

    @Resource
    EmbeddingUtils embeddingUtils;
    @Resource
    EmbeddingClient embeddingClient;


    @Test
    void embed() {
        List<String> texts = List.of("你好，我是帅哥","hehehehehehehwoshi","你很酷吗？");
        List<float[]> var1 = embeddingClient.embed(texts);
        List<float[]> var2 = embeddingUtils.embed(texts);
        assertEquals(var1.size(), var2.size(), "列表长度不一致");
        for (int i = 0; i < var1.size(); i++) {
            // 这里调用的是支持 float[] 的 assertArrayEquals
            assertArrayEquals(var1.get(i), var2.get(i), 0.001f, "第 " + i + " 个向量不匹配");
        }
    }
}