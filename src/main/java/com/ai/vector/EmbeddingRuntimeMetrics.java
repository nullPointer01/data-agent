package com.ai.vector;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 记录不含正文、向量和凭据的 Embedding 运行指标。
 *
 * @author data-agent
 */
@Component
public class EmbeddingRuntimeMetrics {

    private static final String CALLS = "data_agent_embedding_calls_total";
    private static final String RETRIES = "data_agent_embedding_retries_total";
    private static final String CIRCUIT_REJECTIONS = "data_agent_embedding_circuit_rejections_total";
    private static final String CALL_DURATION = "data_agent_embedding_call_duration";
    private static final String BATCH_SIZE = "data_agent_embedding_batch_size";
    private static final int MAX_MODEL_TAG_LENGTH = 64;

    private final MeterRegistry meterRegistry;

    /**
     * 创建 Embedding 指标记录器。
     *
     * @param meterRegistry 应用统一指标注册表
     */
    public EmbeddingRuntimeMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void recordCall(EmbeddingCallExecutor.Operation operation, String outcome,
            EmbeddingProfile profile, long elapsedNanos) {
        String[] tags = callTags(operation, outcome, profile);
        meterRegistry.counter(CALLS, tags).increment();
        meterRegistry.timer(CALL_DURATION, tags).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    void recordRetry(EmbeddingCallExecutor.Operation operation, EmbeddingProfile profile) {
        meterRegistry.counter(RETRIES,
                "operation", operation.tag(),
                "modelId", normalizeModelId(profile.modelId())).increment();
    }

    void recordCircuitRejection(EmbeddingCallExecutor.Operation operation,
            EmbeddingProfile profile) {
        meterRegistry.counter(CIRCUIT_REJECTIONS,
                "operation", operation.tag(),
                "modelId", normalizeModelId(profile.modelId())).increment();
    }

    void recordBatchSize(EmbeddingProfile profile, int batchSize) {
        meterRegistry.summary(BATCH_SIZE,
                "operation", EmbeddingCallExecutor.Operation.BATCH.tag(),
                "modelId", normalizeModelId(profile.modelId())).record(batchSize);
    }

    private String[] callTags(EmbeddingCallExecutor.Operation operation, String outcome,
            EmbeddingProfile profile) {
        return new String[] {
                "operation", operation.tag(),
                "outcome", outcome,
                "modelId", normalizeModelId(profile.modelId())
        };
    }

    private String normalizeModelId(String modelId) {
        String normalized = modelId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (normalized.length() > MAX_MODEL_TAG_LENGTH) {
            return normalized.substring(0, MAX_MODEL_TAG_LENGTH);
        }
        return normalized;
    }
}
