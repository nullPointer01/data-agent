package com.ai.agent.orchestrator;

import com.ai.logging.RequestCorrelationContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParallelTaskExecutorTest {

    @Test
    void supplyAsyncPropagatesSecurityContext() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("user-1", null));
            ParallelTaskExecutor executor = new ParallelTaskExecutor(executorService);

            String principal = executor.supplyAsync(() -> SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getPrincipal()
                    .toString()).join();

            assertEquals("user-1", principal);
        } finally {
            SecurityContextHolder.clearContext();
            executorService.shutdownNow();
        }
    }

    @Test
    void supplyAsyncPropagatesMdcContext() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            MDC.put(RequestCorrelationContext.MDC_REQUEST_ID, "req-1");
            ParallelTaskExecutor executor = new ParallelTaskExecutor(executorService);

            String requestId = executor.supplyAsync(() -> MDC.get(RequestCorrelationContext.MDC_REQUEST_ID)).join();

            assertEquals("req-1", requestId);
        } finally {
            MDC.clear();
            executorService.shutdownNow();
        }
    }
}
