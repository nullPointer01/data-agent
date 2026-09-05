package com.ai.vector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Embedding 模型网关，统一通过 OpenAI 兼容服务生成向量。
 *
 * @author data-agent
 */
@Component
public class EmbeddingGateway {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingGateway.class);
    private static final String API_PROVIDER = "api";
    private static final String DIMENSION_PROBE_TEXT = "data-agent embedding dimension probe";

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProfile profile;
    private final EmbeddingCompatibilityValidator compatibilityValidator;
    private final EmbeddingInputFormatter inputFormatter;
    private final EmbeddingCallExecutor callExecutor;

    /**
     * 创建仅使用 OpenAI-compatible API 的 Embedding Gateway。
     *
     * @param properties Embedding 配置
     * @param compatibilityValidator 返回向量契约校验器
     * @param inputFormatter query/document 输入格式化器
     * @param callExecutor 外部调用治理执行器
     */
    public EmbeddingGateway(EmbeddingProperties properties,
            EmbeddingCompatibilityValidator compatibilityValidator,
            EmbeddingInputFormatter inputFormatter,
            EmbeddingCallExecutor callExecutor) {
        this.compatibilityValidator = compatibilityValidator;
        this.inputFormatter = inputFormatter;
        this.callExecutor = callExecutor;
        EmbeddingProperties.Api api = properties.getApi();
        if (api == null) {
            throw new IllegalArgumentException("Embedding 配置不能为空: app.embedding.api");
        }
        String baseUrl = requireText(api.getBaseUrl(), "app.embedding.api.base-url");
        String modelId = requireText(api.getModelName(), "app.embedding.api.model-name");
        int dimension = requirePositive(properties.getDimension(), "app.embedding.dimension");
        api.validateRuntime(dimension);

        this.profile = new EmbeddingProfile(API_PROVIDER, modelId, properties.getIndexVersion(),
                dimension, properties.isNormalize(), properties.getMetric());

        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .modelName(modelId)
                .baseUrl(baseUrl)
                .timeout(api.getTimeout())
                .maxRetries(0);
        if (api.getOutputDimensions() != null) {
            builder.dimensions(api.getOutputDimensions());
        }
        if (StringUtils.hasText(api.getApiKey())) {
            builder.apiKey(api.getApiKey());
        } else {
            builder.apiKey("no-key");
        }
        this.embeddingModel = builder.build();
        log.info("Embedding 外部 API 客户端已配置: profile={}, timeoutMs={}, requestedDimensions={}",
                profile.identity(), api.getTimeout().toMillis(), api.getOutputDimensions());
    }

    /**
     * 启动阶段真实调用一次模型，避免维度错误延迟到首次业务写入才暴露。
     */
    @PostConstruct
    public void probeDimension() {
        try {
            String probeInput = inputFormatter.format(DIMENSION_PROBE_TEXT, EmbeddingPurpose.PROBE);
            callExecutor.execute(profile, EmbeddingCallExecutor.Operation.PROBE, 0,
                    () -> prepare(embeddingModel.embed(probeInput).content(), "startup-probe"));
            log.info("Embedding Profile 校验通过: {}", profile.identity());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Embedding 启动探测失败: profile=" + profile.identity()
                    + ", cause=" + e.getMessage(), e);
        }
    }

    /**
     * 使用 query 输入契约生成单条检索向量。
     *
     * @param query 原始查询文本
     * @return 已校验并按 Profile 归一化的向量
     */
    public Embedding embedQuery(String query) {
        String formatted = inputFormatter.format(query, EmbeddingPurpose.QUERY);
        return callExecutor.execute(profile, EmbeddingCallExecutor.Operation.QUERY, 0,
                () -> prepare(embeddingModel.embed(formatted).content(), "query"));
    }

    /**
     * 使用 document 输入契约生成单条文档向量。
     *
     * @param segment 原始文档片段
     * @return 已校验并按 Profile 归一化的向量
     */
    public Embedding embedDocument(TextSegment segment) {
        TextSegment formatted = inputFormatter.format(segment, EmbeddingPurpose.DOCUMENT);
        return callExecutor.execute(profile, EmbeddingCallExecutor.Operation.DOCUMENT, 0,
                () -> prepare(embeddingModel.embed(formatted).content(), "document"));
    }

    /**
     * 使用 document 输入契约批量生成文档向量，整批校验成功后才返回。
     *
     * @param segments 原始文档片段，顺序即返回向量顺序
     * @return 已校验并按 Profile 归一化的向量列表
     */
    public List<Embedding> embedDocuments(List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        List<TextSegment> formatted = segments.stream()
                .map(segment -> inputFormatter.format(segment, EmbeddingPurpose.DOCUMENT))
                .toList();
        return callExecutor.execute(profile, EmbeddingCallExecutor.Operation.BATCH, formatted.size(),
                () -> prepareBatch(embeddingModel.embedAll(formatted).content(), formatted.size()));
    }

    /**
     * 返回当前向量索引要求的固定维度。
     *
     * @return Profile 期望维度
     */
    public int getDimension() {
        return profile.dimension();
    }

    /**
     * 返回当前模型和 Collection 的统一身份契约。
     *
     * @return Embedding Profile
     */
    public EmbeddingProfile getProfile() {
        return profile;
    }

    private List<Embedding> prepareBatch(List<Embedding> embeddings, int expectedCount) {
        compatibilityValidator.validateBatch(profile, embeddings, expectedCount, "batch");
        if (profile.normalize()) {
            embeddings.forEach(Embedding::normalize);
        }
        return List.copyOf(embeddings);
    }

    private Embedding prepare(Embedding embedding, String operation) {
        compatibilityValidator.validate(profile, embedding, operation);
        if (profile.normalize()) {
            embedding.normalize();
        }
        return embedding;
    }

    private String requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Embedding 配置不能为空: " + propertyName);
        }
        return value.trim();
    }

    private int requirePositive(int value, String propertyName) {
        if (value <= 0) {
            throw new IllegalArgumentException("Embedding 配置必须为正整数: " + propertyName);
        }
        return value;
    }
}
