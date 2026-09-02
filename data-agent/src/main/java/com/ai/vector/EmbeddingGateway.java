package com.ai.vector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Embedding 模型网关，支持 local（AllMiniLmL6V2）和 api（OpenAI 兼容端点）两种模式。
 *
 * @author data-agent
 */
@Component
public class EmbeddingGateway {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingGateway.class);
    private static final String LOCAL_PROVIDER = "local";
    private static final String API_PROVIDER = "api";
    private static final String LOCAL_MODEL_ID = "all-minilm-l6-v2";
    private static final String DIMENSION_PROBE_TEXT = "data-agent embedding dimension probe";

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProfile profile;
    private final EmbeddingCompatibilityValidator compatibilityValidator;

    public EmbeddingGateway(EmbeddingProperties properties,
            EmbeddingCompatibilityValidator compatibilityValidator) {
        this.compatibilityValidator = compatibilityValidator;
        String provider = normalizeProvider(properties.getProvider());
        String modelId;
        if (API_PROVIDER.equals(provider)) {
            EmbeddingProperties.Api api = properties.getApi();
            modelId = requireText(api.getModelName(), "app.embedding.api.model-name");
            log.info("Embedding 使用 API 模式: model={}, baseUrl={}", api.getModelName(), api.getBaseUrl());
            OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                    .modelName(modelId)
                    .baseUrl(requireText(api.getBaseUrl(), "app.embedding.api.base-url"));
            if (StringUtils.hasText(api.getApiKey())) {
                builder.apiKey(api.getApiKey());
            } else {
                builder.apiKey("no-key");
            }
            this.embeddingModel = builder.build();
        } else {
            modelId = LOCAL_MODEL_ID;
            log.info("Embedding 使用本地模式: AllMiniLmL6V2（384 维）");
            this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        }
        this.profile = new EmbeddingProfile(provider, modelId, properties.getIndexVersion(),
                properties.getDimension(), properties.isNormalize(), properties.getMetric());
    }

    /**
     * 启动阶段真实调用一次模型，避免维度错误延迟到首次业务写入才暴露。
     */
    @PostConstruct
    public void probeDimension() {
        try {
            prepare(embeddingModel.embed(DIMENSION_PROBE_TEXT).content(), "startup-probe");
            log.info("Embedding Profile 校验通过: {}", profile.identity());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Embedding 启动探测失败: profile=" + profile.identity()
                    + ", cause=" + e.getMessage(), e);
        }
    }

    public Embedding embed(String text) {
        return prepare(embeddingModel.embed(text).content(), "embed-text");
    }

    public Embedding embed(TextSegment segment) {
        return prepare(embeddingModel.embed(segment).content(), "embed-segment");
    }

    public int getDimension() {
        return profile.dimension();
    }

    public EmbeddingProfile getProfile() {
        return profile;
    }

    private Embedding prepare(Embedding embedding, String operation) {
        compatibilityValidator.validate(profile, embedding, operation);
        if (profile.normalize()) {
            embedding.normalize();
        }
        return embedding;
    }

    private String normalizeProvider(String provider) {
        String normalized = requireText(provider, "app.embedding.provider").toLowerCase(Locale.ROOT);
        if (!LOCAL_PROVIDER.equals(normalized) && !API_PROVIDER.equals(normalized)) {
            throw new IllegalArgumentException("不支持的 Embedding provider: " + provider
                    + "，仅支持 local 或 api");
        }
        return normalized;
    }

    private String requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Embedding 配置不能为空: " + propertyName);
        }
        return value.trim();
    }
}
