package com.ai.resilience;

import com.ai.agent.context.AgentContextGovernor.ContextLimitException;
import com.ai.agent.runtime.AgentRunTerminatedException;
import com.ai.mcp.ModelHttpException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RetriableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.io.EOFException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 统一外部调用治理：Resilience4j 管理重试、滑动窗口及单次半开探测。
 * 熔断统计包在重试外层，一次逻辑调用只记一次成败；取消与本地预算不污染厂商故障率。
 */
public final class ExternalCallPolicy {
    private final CircuitBreakerRegistry circuits;
    private final RetryConfig retryConfig;

    public ExternalCallPolicy(int attempts, Duration backoff, int failureThreshold, Duration openDuration,
            Predicate<Throwable> retryable) {
        circuits = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(failureThreshold).minimumNumberOfCalls(failureThreshold)
                .failureRateThreshold(100).waitDurationInOpenState(openDuration)
                .permittedNumberOfCallsInHalfOpenState(1)
                .ignoreException(ExternalCallPolicy::isLocalTermination).build());
        retryConfig = RetryConfig.custom().maxAttempts(attempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Math.max(1L, backoff.toMillis())))
                .retryOnException(error -> !isLocalTermination(error) && retryable.test(error)).build();
    }

    public <T> T execute(String key, Callable<T> action, Runnable onRetry) throws Exception {
        Retry retry = Retry.of(key, retryConfig);
        retry.getEventPublisher().onRetry(event -> onRetry.run());
        return CircuitBreaker.decorateCallable(circuits.circuitBreaker(key),
                Retry.decorateCallable(retry, () -> {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("外部调用已取消");
                    }
                    return action.call();
                })).call();
    }

    /** 流式请求只接入同一个熔断器，不重试已开始的流，也不阻塞等待完成。 */
    public <T> CompletionStage<T> executeStream(String key, Supplier<CompletionStage<T>> action) {
        return CircuitBreaker.decorateCompletionStage(circuits.circuitBreaker(key), action).get();
    }

    public static boolean isLocalTermination(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException || cause instanceof CancellationException
                    || cause instanceof AgentRunTerminatedException || cause instanceof ContextLimitException) {
                return true;
            }
        }
        return false;
    }

    /** 协议错误优先于底层网络 cause；未知错误与响应合同错误不盲目重试。 */
    public static boolean isTransient(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ModelHttpException http) {
                return http.isRetriable();
            }
            if (cause instanceof NonRetriableException) {
                return false;
            }
            if (cause instanceof RetriableException) {
                return true;
            }
            if (cause instanceof HttpException http) {
                return http.statusCode() == 429 || http.statusCode() >= 500;
            }
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketException || cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException || cause instanceof EOFException
                    || cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
