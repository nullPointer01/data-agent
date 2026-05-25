package com.ai.config;

import com.ai.logging.RequestCorrelationContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncConfigTest {

    @Test
    void executorsPropagateRequestAndSecurityContext() throws Exception {
        AsyncConfig config = new AsyncConfig();
        Executor executor = config.agentTaskExecutor(1, 1, 10, config.contextPropagatingTaskDecorator());
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();
        MDC.put(RequestCorrelationContext.MDC_REQUEST_ID, "req-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-1", null));

        taskExecutor.execute(() -> future.complete(MDC.get(RequestCorrelationContext.MDC_REQUEST_ID) + ":"
                + SecurityContextHolder.getContext().getAuthentication().getPrincipal()));

        assertEquals("req-1:user-1", future.get(2, TimeUnit.SECONDS));
        taskExecutor.shutdown();
        MDC.clear();
        SecurityContextHolder.clearContext();
    }
}
