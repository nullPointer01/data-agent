package com.ai.vector;

import com.ai.resilience.ExternalCallPolicy;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Supplier;

/** Embedding 按 Profile 隔离故障策略，重试和半开探测由 Resilience4j 管理。 */
@Component
public class EmbeddingCallExecutor {
    private final ExternalCallPolicy policy;
    private final EmbeddingRuntimeMetrics metrics;

    public EmbeddingCallExecutor(EmbeddingProperties properties, EmbeddingRuntimeMetrics metrics) {
        EmbeddingProperties.Api api = Objects.requireNonNull(properties.getApi(), "Embedding 配置不能为空");
        api.validateRuntime(properties.getDimension());
        policy = new ExternalCallPolicy(api.getMaxAttempts(), api.getInitialBackoff(),
                api.getCircuitFailureThreshold(), api.getCircuitOpenDuration(), ExternalCallPolicy::isTransient);
        this.metrics = metrics;
    }

    public <T> T execute(EmbeddingProfile profile, Operation operation, int batchSize, Supplier<T> action) {
        Objects.requireNonNull(profile, "Embedding Profile 不能为空");
        Objects.requireNonNull(operation, "Embedding operation 不能为空");
        Objects.requireNonNull(action, "Embedding action 不能为空");
        if (operation == Operation.BATCH && batchSize <= 0) {
            throw new IllegalArgumentException("Embedding 批大小必须为正整数");
        }
        long started = System.nanoTime();
        String outcome = "failure";
        try {
            if (operation == Operation.BATCH) {
                metrics.recordBatchSize(profile, batchSize);
            }
            T value = policy.execute(profile.identity(), action::get, () -> metrics.recordRetry(operation, profile));
            outcome = "success";
            return value;
        } catch (CallNotPermittedException exception) {
            outcome = "rejected";
            metrics.recordCircuitRejection(operation, profile);
            throw new EmbeddingCallRejectedException(profile.identity(), operation.tag());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding 调用已中断", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Embedding 调用失败", exception);
        } finally {
            metrics.recordCall(operation, outcome, profile, System.nanoTime() - started);
        }
    }

    /** 指标中的低基数操作标签。 */
    public enum Operation {
        PROBE("probe"), QUERY("query"), DOCUMENT("document"), BATCH("batch");
        private final String tag;
        Operation(String tag) { this.tag = tag; }
        public String tag() { return tag; }
    }
}
