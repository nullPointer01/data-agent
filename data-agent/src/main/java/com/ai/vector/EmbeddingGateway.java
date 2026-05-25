package com.ai.vector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Embedding 模型网关，支持 local（AllMiniLmL6V2）和 api（OpenAI 兼容端点）两种模式。
 *
 * @author data-agent
 */
@Component
public class EmbeddingGateway {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingGateway.class);

    private final EmbeddingModel embeddingModel;
    private final int dimension;

    public EmbeddingGateway(EmbeddingProperties properties) {
        if ("api".equalsIgnoreCase(properties.getProvider())) {
            EmbeddingProperties.Api api = properties.getApi();
            log.info("Embedding 使用 API 模式: model={}, baseUrl={}", api.getModelName(), api.getBaseUrl());
            OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                    .modelName(api.getModelName())
                    .baseUrl(api.getBaseUrl());
            if (StringUtils.hasText(api.getApiKey())) {
                builder.apiKey(api.getApiKey());
            } else {
                builder.apiKey("no-key");
            }
            this.embeddingModel = builder.build();
        } else {
            log.info("Embedding 使用本地模式: AllMiniLmL6V2（384 维）");
            this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        }
        this.dimension = properties.getDimension();
    }

    public Embedding embed(String text) {
        return embeddingModel.embed(text).content();
    }

    public Embedding embed(TextSegment segment) {
        return embeddingModel.embed(segment).content();
    }

    public int getDimension() {
        return dimension;
    }
}
