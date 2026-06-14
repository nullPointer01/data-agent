package com.ai.mcp;

import dev.ai4j.openai4j.OpenAiHttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行带重试和断路器保护的模型调用。
 *
 * @author data-agent
 */
@Component
public class ModelRetryExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRetryExecutor.class);
    private static final int FAILURE_THRESHOLD = 3;
    private static final long CIRCUIT_OPEN_MS = 60_000L;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1_000L;
    private static final int MILLISECONDS_PER_SECOND = 1000;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVER_ERROR_MIN = 500;
    private static final String ERROR_CIRCUIT_PREFIX = "模型[";

    private final Map<String, AtomicInteger> failureCount = new ConcurrentHashMap<>();
    private final Map<String, Long> circuitOpenUntil = new ConcurrentHashMap<>();
    private final Sleeper sleeper;

    public ModelRetryExecutor() {
        this(Thread::sleep);
    }

    ModelRetryExecutor(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    /**
     * 执行带重试和断路器的单次模型调用。
     *
     * @param <T> 调用结果类型
     * @param action 模型调用操作
     * @param modelKey 模型密钥
     * @return 模型响应
     * @throws Exception 调用异常
     */
    public <T> T execute(Callable<T> action, String modelKey) throws Exception {
        checkCircuitBreaker(modelKey);
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                T result = action.call();
                recordSuccess(modelKey);
                return result;
            } catch (Exception e) {
                lastException = e;
                if (!isRetriable(e)) {
                    LOGGER.warn("Model call failed without retry | model={} | error={}", modelKey, e.getMessage());
                    recordFailure(modelKey);
                    throw e;
                }
                handleFailureAttempt(modelKey, attempt, e);
            }
        }
        throw lastException;
    }

    private boolean isRetriable(Exception exception) {
        if (exception instanceof ModelHttpException modelHttpException) {
            return modelHttpException.isRetriable();
        }
        // LangChain4j 路径按 HTTP 状态码判定：401/403/404 等认证、路径类错误重试无意义
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof OpenAiHttpException openAiHttpException) {
                int code = openAiHttpException.code();
                return code == HTTP_TOO_MANY_REQUESTS || code >= HTTP_SERVER_ERROR_MIN;
            }
            cause = cause.getCause();
        }
        return true;
    }

    private void checkCircuitBreaker(String modelKey) {
        Long openUntil = circuitOpenUntil.get(modelKey);
        if (openUntil != null && System.currentTimeMillis() < openUntil) {
            long remainSec = (openUntil - System.currentTimeMillis()) / MILLISECONDS_PER_SECOND;
            throw new IllegalStateException(ERROR_CIRCUIT_PREFIX + modelKey + "]熔断中，请" + remainSec + "秒后重试");
        }
    }

    private void handleFailureAttempt(String modelKey, int attempt, Exception exception) throws InterruptedException {
        if (attempt < MAX_RETRIES - 1) {
            long delayMs = INITIAL_RETRY_DELAY_MS * (1L << attempt);
            LOGGER.warn("Model call failed (attempt {}/{}), retrying after {}ms | model={} | error={}",
                    attempt + 1, MAX_RETRIES, delayMs, modelKey, exception.getMessage());
            sleeper.sleep(delayMs);
            return;
        }
        LOGGER.error("Model call failed after {} attempts | model={} | error={}",
                MAX_RETRIES, modelKey, exception.getMessage());
        recordFailure(modelKey);
    }

    private void recordSuccess(String modelKey) {
        failureCount.remove(modelKey);
        circuitOpenUntil.remove(modelKey);
    }

    private void recordFailure(String modelKey) {
        int count = failureCount.computeIfAbsent(modelKey, key -> new AtomicInteger(0))
                .incrementAndGet();
        if (count >= FAILURE_THRESHOLD) {
            long openUntil = System.currentTimeMillis() + CIRCUIT_OPEN_MS;
            circuitOpenUntil.put(modelKey, openUntil);
            LOGGER.warn("Circuit breaker OPEN for model[{}]: {} consecutive failures, open for {}ms",
                    modelKey, count, CIRCUIT_OPEN_MS);
        }
    }

    /**
     * Sleep abstraction for deterministic retry tests.
     *
     * @author data-agent
     */
    @FunctionalInterface
    interface Sleeper {

        /**
         * Sleeps for the provided delay.
         *
         * @param delayMs delay in milliseconds
         * @throws InterruptedException when interrupted
         */
        void sleep(long delayMs) throws InterruptedException;
    }
}
