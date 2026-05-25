package com.ai.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

/**
 * 在线程池任务中传播日志 MDC 和 Spring Security 上下文。
 *
 * @author data-agent
 */
public class ContextPropagatingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> capturedMdcContext = MDC.getCopyOfContextMap();
        SecurityContext capturedSecurityContext = SecurityContextHolder.getContext();
        return () -> runWithCapturedContext(runnable, capturedMdcContext, capturedSecurityContext);
    }

    private void runWithCapturedContext(Runnable runnable, Map<String, String> capturedMdcContext,
            SecurityContext capturedSecurityContext) {
        Map<String, String> previousMdcContext = MDC.getCopyOfContextMap();
        SecurityContext previousSecurityContext = SecurityContextHolder.getContext();
        try {
            restoreMdcContext(capturedMdcContext);
            SecurityContextHolder.setContext(capturedSecurityContext);
            runnable.run();
        } finally {
            restoreMdcContext(previousMdcContext);
            SecurityContextHolder.setContext(previousSecurityContext);
        }
    }

    private void restoreMdcContext(Map<String, String> contextMap) {
        if (contextMap == null || contextMap.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(contextMap);
    }
}
