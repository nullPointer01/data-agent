package com.ai.config;

import com.ai.logging.RequestCorrelationContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContextPropagatingTaskDecoratorTest {

    @Test
    void decoratePropagatesMdcAndSecurityContextThenRestoresPreviousContext() {
        ContextPropagatingTaskDecorator decorator = new ContextPropagatingTaskDecorator();
        MDC.put(RequestCorrelationContext.MDC_REQUEST_ID, "req-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-1", null));
        Runnable decorated = decorator.decorate(() -> {
            MDC.put("inside", "changed");
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("changed", null));
        });
        MDC.clear();
        SecurityContextHolder.clearContext();

        decorated.run();

        assertNull(MDC.get("inside"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void decorateMakesCapturedContextVisibleInsideTask() {
        ContextPropagatingTaskDecorator decorator = new ContextPropagatingTaskDecorator();
        MDC.put(RequestCorrelationContext.MDC_REQUEST_ID, "req-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-1", null));
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> principal = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            requestId.set(MDC.get(RequestCorrelationContext.MDC_REQUEST_ID));
            principal.set(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        });
        MDC.clear();
        SecurityContextHolder.clearContext();
        decorated.run();

        assertEquals("req-1", requestId.get());
        assertEquals("user-1", principal.get());
    }
}
