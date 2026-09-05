package com.ai.vector;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RetriableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.EOFException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 为外部 Embedding 调用提供分类重试、指数退避和按 Profile 隔离的熔断保护。
 *
 * @author data-agent
 */
@Component
public class EmbeddingCallExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingCallExecutor.class);
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVER_ERROR_MIN = 500;

    private final int maxAttempts;
    private final long initialBackoffMillis;
    private final int circuitFailureThreshold;
    private final long circuitOpenMillis;
    private final EmbeddingRuntimeMetrics metrics;
    private final Map<String, CircuitState> circuitStates = new ConcurrentHashMap<>();

    /**
     * 从 Embedding 配置创建独立的调用治理执行器。
     *
     * @param properties Embedding 运行配置
     * @param metrics 安全指标记录器
     */
    public EmbeddingCallExecutor(EmbeddingProperties properties, EmbeddingRuntimeMetrics metrics) {
        EmbeddingProperties.Api api = Objects.requireNonNull(properties.getApi(),
                "Embedding 配置不能为空: app.embedding.api");
        api.validateRuntime(properties.getDimension());
        this.maxAttempts = api.getMaxAttempts();
        this.initialBackoffMillis = positiveMillis(api.getInitialBackoff());
        this.circuitFailureThreshold = api.getCircuitFailureThreshold();
        this.circuitOpenMillis = positiveMillis(api.getCircuitOpenDuration());
        this.metrics = metrics;
    }

    /**
     * 执行一次受治理的 Embedding 逻辑调用。
     *
     * @param profile 当前 Embedding Profile
     * @param operation 调用操作
     * @param batchSize 批量操作的输入数量，非批量操作传 0
     * @param action 实际 SDK 调用
     * @param <T> 调用结果类型
     * @return SDK 调用结果
     */
    public <T> T execute(EmbeddingProfile profile, Operation operation, int batchSize,
            Supplier<T> action) {
        Objects.requireNonNull(profile, "Embedding Profile 不能为空");
        Objects.requireNonNull(operation, "Embedding operation 不能为空");
        Objects.requireNonNull(action, "Embedding action 不能为空");
        if (operation == Operation.BATCH && batchSize <= 0) {
            throw new IllegalArgumentException("Embedding 批大小必须为正整数");
        }

        long startedNanos = System.nanoTime();
        String outcome = "failure";
        CircuitState circuit = circuitStates.computeIfAbsent(profile.identity(), ignored -> new CircuitState());
        try {
            acquireCircuitPermission(circuit, profile, operation);
            if (operation == Operation.BATCH) {
                metrics.recordBatchSize(profile, batchSize);
            }
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    T result = action.get();
                    recordSuccess(circuit);
                    outcome = "success";
                    return result;
                } catch (RuntimeException exception) {
                    if (!isRetriable(exception) || attempt == maxAttempts) {
                        boolean opened = recordFailure(circuit);
                        logFinalFailure(profile, operation, exception, attempt, opened);
                        throw exception;
                    }
                    metrics.recordRetry(operation, profile);
                    long delayMillis = retryDelayMillis(attempt);
                    LOGGER.warn("Embedding 调用将在退避后重试: operation={}, profile={}, attempt={}/{}, delayMs={}, exception={}",
                            operation.tag(), profile.identity(), attempt, maxAttempts, delayMillis,
                            exception.getClass().getName());
                    sleep(delayMillis, circuit, profile, operation);
                }
            }
            throw new IllegalStateException("Embedding 调用未产生结果: operation=" + operation.tag()
                    + ", profile=" + profile.identity());
        } catch (EmbeddingCallRejectedException exception) {
            outcome = "rejected";
            metrics.recordCircuitRejection(operation, profile);
            throw exception;
        } finally {
            metrics.recordCall(operation, outcome, profile, System.nanoTime() - startedNanos);
        }
    }

    private void acquireCircuitPermission(CircuitState circuit, EmbeddingProfile profile,
            Operation operation) {
        long now = System.currentTimeMillis();
        synchronized (circuit) {
            if (circuit.openUntilMillis > now) {
                throw rejected(profile, operation, circuit.openUntilMillis - now);
            }
            if (circuit.openUntilMillis > 0L) {
                if (circuit.recoveryCallInProgress) {
                    throw rejected(profile, operation, 0L);
                }
                circuit.recoveryCallInProgress = true;
            }
        }
    }

    private EmbeddingCallRejectedException rejected(EmbeddingProfile profile, Operation operation,
            long remainingMillis) {
        return new EmbeddingCallRejectedException(profile.identity(), operation.tag(), remainingMillis);
    }

    private boolean recordFailure(CircuitState circuit) {
        synchronized (circuit) {
            circuit.consecutiveFailures++;
            if (circuit.recoveryCallInProgress
                    || circuit.consecutiveFailures >= circuitFailureThreshold) {
                circuit.openUntilMillis = addWithoutOverflow(System.currentTimeMillis(), circuitOpenMillis);
                circuit.recoveryCallInProgress = false;
                return true;
            }
            return false;
        }
    }

    private void recordSuccess(CircuitState circuit) {
        synchronized (circuit) {
            circuit.consecutiveFailures = 0;
            circuit.openUntilMillis = 0L;
            circuit.recoveryCallInProgress = false;
        }
    }

    private boolean isRetriable(RuntimeException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof NonRetriableException) {
                return false;
            }
            if (cause instanceof RetriableException) {
                return true;
            }
            if (cause instanceof HttpException httpException) {
                int status = httpException.statusCode();
                return status == HTTP_TOO_MANY_REQUESTS || status >= HTTP_SERVER_ERROR_MIN;
            }
            cause = cause.getCause();
        }

        cause = exception;
        while (cause != null) {
            if (cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException
                    || cause instanceof NoRouteToHostException
                    || cause instanceof EOFException
                    || cause instanceof SocketException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void sleep(long delayMillis, CircuitState circuit, EmbeddingProfile profile,
            Operation operation) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            boolean opened = recordFailure(circuit);
            LOGGER.warn("Embedding 重试退避被中断: operation={}, profile={}, circuitOpened={}",
                    operation.tag(), profile.identity(), opened);
            throw new IllegalStateException("Embedding 重试退避被中断: operation=" + operation.tag()
                    + ", profile=" + profile.identity(), exception);
        }
    }

    private void logFinalFailure(EmbeddingProfile profile, Operation operation,
            RuntimeException exception, int attempts, boolean circuitOpened) {
        LOGGER.warn("Embedding 调用最终失败: operation={}, profile={}, attempts={}, circuitOpened={}, exception={}",
                operation.tag(), profile.identity(), attempts, circuitOpened,
                exception.getClass().getName());
    }

    private long retryDelayMillis(int completedAttempt) {
        int shift = completedAttempt - 1;
        if (initialBackoffMillis > (Long.MAX_VALUE >> shift)) {
            return Long.MAX_VALUE;
        }
        return initialBackoffMillis << shift;
    }

    private long positiveMillis(Duration duration) {
        return Math.max(1L, duration.toMillis());
    }

    private long addWithoutOverflow(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    /**
     * Embedding 指标允许使用的固定操作集合。
     */
    public enum Operation {

        /** 启动维度探测。 */
        PROBE("probe"),

        /** 单条检索查询。 */
        QUERY("query"),

        /** 单条文档索引。 */
        DOCUMENT("document"),

        /** 批量文档索引。 */
        BATCH("batch");

        private final String tag;

        Operation(String tag) {
            this.tag = tag;
        }

        /**
         * 返回低基数指标标签。
         *
         * @return 固定操作标签
         */
        public String tag() {
            return tag;
        }
    }

    private static final class CircuitState {

        private int consecutiveFailures;
        private long openUntilMillis;
        private boolean recoveryCallInProgress;
    }
}
