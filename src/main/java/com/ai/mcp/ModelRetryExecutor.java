package com.ai.mcp;

import com.ai.resilience.ExternalCallPolicy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** 模型按身份共享 Resilience4j 熔断器；每次实际调用的预算由模型网关检查。 */
@Component
public class ModelRetryExecutor {
    private final ExternalCallPolicy policy = new ExternalCallPolicy(3, Duration.ofSeconds(1),
            3, Duration.ofSeconds(60), ExternalCallPolicy::isTransient);

    public <T> T execute(Callable<T> action, String modelKey) throws Exception {
        return policy.execute(modelKey, action, () -> { });
    }

    /** 流式与同步共用熔断状态，流式始终只尝试一次。 */
    public <T> CompletionStage<T> executeStream(Supplier<CompletionStage<T>> action, String modelKey) {
        return policy.executeStream(modelKey, action);
    }
}
