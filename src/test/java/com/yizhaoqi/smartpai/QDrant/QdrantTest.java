package com.yizhaoqi.smartpai.QDrant;

import com.yizhaoqi.smartpai.entity.TextChunk;
import com.yizhaoqi.smartpai.qdrant.QDrantUtils;
import com.yizhaoqi.smartpai.utils.PointStructUtils;
import io.qdrant.client.grpc.Points;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author caiyuping
 * @date 2026/5/20 21:34
 * @description: QDrant 向量数据库测试
 */
@SpringBootTest
public class QdrantTest {

    @Resource
    private QDrantUtils qDrantUtils;

    @Test
    void testUpsertPoints() {
        List<TextChunk> chunks = Arrays.asList(
                new TextChunk(1, "ChuYu知识库是一个基于RAG的企业级AI知识管理系统"),
                new TextChunk(2, "系统支持多模态文档处理，包括PDF识图、图片OCR和文本解析"),
                new TextChunk(3, "混合检索引擎结合了KNN向量搜索和BM25关键词匹配")
        );

        float[] vector1 = new float[4096];
        float[] vector2 = new float[4096];
        float[] vector3 = new float[4096];
        for (int i = 0; i < 4096; i++) {
            vector1[i] = (float) (Math.random() * 0.1);
            vector2[i] = (float) (Math.random() * 0.1);
            vector3[i] = (float) (Math.random() * 0.1);
        }
        List<float[]> vectors = Arrays.asList(vector1, vector2, vector3);

        // 3. 封装为 PointStruct
        List<Points.PointStruct> points = PointStructUtils.getPointStruct(
                chunks, vectors,
                "test-md5-001",
                "1",
                "default",
                true
        );

        System.out.println("封装PointStruct数量: " + points.size());
        points.forEach(p -> System.out.println("  pointId=" + p.getId().getNum()
                + ", chunkId=" + p.getPayloadMap().get("chunkId").getIntegerValue()
                + ", textContent=" + p.getPayloadMap().get("textContent").getStringValue().substring(0, Math.min(20, p.getPayloadMap().get("textContent").getStringValue().length())) + "..."));

        // 4. 写入 Qdrant
        qDrantUtils.updateOperationInfo(points);
        System.out.println("向量入库成功");

        //查找
        System.out.println("开始查找");
        List<Float> vectorList = new ArrayList<>(vector1.length);
        for (float v : vector1) {
            vectorList.add(v);
        }
        List<Points.ScoredPoint> operationInfo = qDrantUtils.getOperationInfo(vectorList);
        for (Points.ScoredPoint scoredPoint : operationInfo) {
            System.out.println("查询结果: pointId=" + scoredPoint.getId().getNum()
                    + ", score=" + scoredPoint.getScore()
                    + ", chunkId=" + scoredPoint.getPayloadMap().get("chunkId").getIntegerValue()
                    + ", textContent=" + scoredPoint.getPayloadMap().get("textContent").getStringValue().substring(0, Math.min(20, scoredPoint.getPayloadMap().get("textContent").getStringValue().length())) + "...");
        }
    }
}
