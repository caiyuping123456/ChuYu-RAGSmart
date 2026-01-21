package com.yizhaoqi.smartpai.langchain4j.embedding;


import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class EmbeddingUtils {

    @Resource
    EmbeddingModel embeddingModel;

    /**
     * 这个是Langchain4j的嵌入模型
     * @param texts
     * @return
     */
    public List<float[]> embed(List<String> texts){

        log.info("开始调用langchain4j框架请求");
        List<TextSegment> var =  texts.stream()
                .map(item -> new TextSegment(item,new Metadata()))
                .collect(Collectors.toList());

        /**
         * 批量进行请求
         */
        Response<List<Embedding>> listResponse = embeddingModel.embedAll(var);
        List<Embedding> content = listResponse.content();

        /**
         * 封装请求
         */
        log.info("请求完成，正在返回");
        return content.stream().map(item -> item.vector()).collect(Collectors.toList());
    }
}
