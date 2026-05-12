package com.yizhaoqi.smartpai.utils;

import com.yizhaoqi.smartpai.entity.TextChunk;
import io.qdrant.client.grpc.Points;

import java.util.*;
import java.util.stream.Collectors;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

/**
 * @author caiyuping
 * @date 2026/5/19 21:47
 * @description: 封装Qdrant PointStruct的工具类
 */
public class PointStructUtils {

	/**
	 * 将分块文本和向量封装为Qdrant的PointStruct列表
	 *
	 * @param chunks   文档分块
	 * @param vectors  向量（与chunks一一对应）
	 * @param fileMd5  文件指纹
	 * @param userId   用户ID
	 * @param orgTag   组织标签
	 * @param isPublic 是否公开
	 * @return PointStruct列表
	 */
	public static List<Points.PointStruct> getPointStruct(List<TextChunk> chunks,
	                                                      List<float[]> vectors,
	                                                      String fileMd5,
	                                                      String userId,
	                                                      String orgTag,
	                                                      boolean isPublic) {
		List<Points.PointStruct> list = new ArrayList<>();

		for (int i = 0; i < chunks.size(); i++) {
			TextChunk chunk = chunks.get(i);
			float[] vector = vectors.get(i);

			// float[] -> List<Float>
			List<Float> vectorList = new ArrayList<>(vector.length);
			for (float v : vector) {
				vectorList.add(v);
			}

			Points.PointStruct pointStruct = Points.PointStruct.newBuilder()
					.setId(id(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE))
					.setVectors(vectors(vectorList))
					.putAllPayload(Map.of(
							"fileMd5", value(fileMd5),
							"chunkId", value(chunk.getChunkId()),
							"textContent", value(chunk.getContent()),
							"modelVersion", value("deepseek-embed"),
							"userId", value(userId),
							"orgTag", value(orgTag),
							"isPublic", value(isPublic)
					))
					.build();
			list.add(pointStruct);
		}
		return list;
	}
}
