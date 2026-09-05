package com.ai.config;

import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
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
        AgentRunContext capturedRunContext = AgentRunScope.current().orElse(null);
        return () -> runWithCapturedContext(runnable, capturedMdcContext, capturedSecurityContext,
                capturedRunContext);
    }

    private void runWithCapturedContext(Runnable runnable, Map<String, String> capturedMdcContext,
            SecurityContext capturedSecurityContext, AgentRunContext capturedRunContext) {
        Map<String, String> previousMdcContext = MDC.getCopyOfContextMap();
        SecurityContext previousSecurityContext = SecurityContextHolder.getContext();
        try {
            restoreMdcContext(capturedMdcContext);
            SecurityContextHolder.setContext(capturedSecurityContext);
            if (capturedRunContext == null) {
                runnable.run();
            } else {
                AgentRunScope.run(capturedRunContext, runnable);
            }
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
