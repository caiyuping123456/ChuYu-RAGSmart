package com.yizhaoqi.smartpai.qdrant;

import com.google.common.util.concurrent.ListenableFuture;
import com.yizhaoqi.smartpai.config.QDrantConfig;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @author caiyuping
 * @date 2026/5/19 21:01
 * @description: 业务
 */
@Component
@Slf4j
public class QDrantUtils {
    @Resource
    private QdrantClient qdrantClient;
    @Resource
    private QDrantConfig qDrantConfig;

    /**
     * 这个是一个工具类，主要是用来创建collection的
     */
    private void createCollection(){
        try {
            qdrantClient.createCollectionAsync(qDrantConfig.getCollection(),
                    Collections.VectorParams.newBuilder()
                            .setDistance(Collections.Distance.Cosine)
                            .setSize(qDrantConfig.getDimension())
                            .build()).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasCollection(){
        ListenableFuture<Collections.CollectionInfo> collectionInfoAsync = qdrantClient.getCollectionInfoAsync(qDrantConfig.getCollection());
        try {
            collectionInfoAsync.get();
            return true;
        } catch (Exception e) {
            return false;  // 获取信息失败，说明集合不存在
        }


    }

    /**
     * 进行向量化存储
     * @param points
     */
    public void updateOperationInfo(List<Points.PointStruct> points){
        // 这里是向量化存储的核心代码，主要是用来将向量化后的数据存储到QDrant中
        //判断是不是有这个集合
        if (!hasCollection()) createCollection();
        try {
            Points.UpdateResult operationInfo = qdrantClient.upsertAsync("test_collection", points).get();
            log.info("向量入库成功", operationInfo);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

    }
}
