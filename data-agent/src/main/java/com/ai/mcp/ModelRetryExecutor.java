package com.ai.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes model calls with retry and circuit breaker protection.
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
     * Executes one model call with retry and circuit breaker.
     *
     * @param action model call action
     * @param modelKey model key
     * @return model response
     * @throws Exception call exception
     */
    public String execute(Callable<String> action, String modelKey) throws Exception {
        checkCircuitBreaker(modelKey);
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String result = action.call();
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
