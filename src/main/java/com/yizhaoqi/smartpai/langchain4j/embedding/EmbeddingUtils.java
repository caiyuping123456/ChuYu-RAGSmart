package com.yizhaoqi.smartpai.langchain4j.embedding;


import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class EmbeddingUtils {

    @Resource
    EmbeddingModel embeddingModel;

    public List<float[]> embed(List<String> texts){

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
        return content.stream().map(item -> item.vector()).collect(Collectors.toList());
    }
}
