package com.ai.mcp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRetryExecutorTest {

    @Test
    void executeRetriesAndReturnsSuccess() throws Exception {
        ModelRetryExecutor executor = new ModelRetryExecutor(delayMs -> {
        });
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary");
            }
            return "ok";
        }, "model-1");

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void executeOpensCircuitAfterConsecutiveFailures() {
        ModelRetryExecutor executor = new ModelRetryExecutor(delayMs -> {
        });

        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalStateException.class,
                    () -> executor.execute(() -> {
                        throw new IllegalStateException("down");
                    }, "model-1"));
        }

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> executor.execute(() -> "ok", "model-1"));
        assertTrue(exception.getMessage().startsWith("模型[model-1]熔断中"));
    }

    @Test
    void executeDoesNotRetryNonRetriableHttpFailures() {
        ModelRetryExecutor executor = new ModelRetryExecutor(delayMs -> {
        });
        AtomicInteger attempts = new AtomicInteger();

        ModelHttpException exception = assertThrows(ModelHttpException.class,
                () -> executor.execute(() -> {
                    attempts.incrementAndGet();
                    throw new ModelHttpException(401, "auth failed", false);
                }, "model-1"));

        assertEquals(401, exception.getStatusCode());
        assertEquals(1, attempts.get());
    }
}
