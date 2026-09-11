package com.ai.config;

import com.ai.agent.capability.AgentCapabilityBindingSnapshot;
import com.ai.agent.capability.AgentCapabilityScope;
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
        AgentCapabilityBindingSnapshot capturedCapabilitySnapshot = AgentCapabilityScope.current().orElse(null);
        return () -> runWithCapturedContext(runnable, capturedMdcContext, capturedSecurityContext,
                capturedRunContext, capturedCapabilitySnapshot);
    }

    private void runWithCapturedContext(Runnable runnable, Map<String, String> capturedMdcContext,
            SecurityContext capturedSecurityContext,
            AgentRunContext capturedRunContext,
            AgentCapabilityBindingSnapshot capturedCapabilitySnapshot) {
        Map<String, String> previousMdcContext = MDC.getCopyOfContextMap();
        SecurityContext previousSecurityContext = SecurityContextHolder.getContext();
        try {
            restoreMdcContext(capturedMdcContext);
            SecurityContextHolder.setContext(capturedSecurityContext);
            Runnable capabilityScoped = capturedCapabilitySnapshot == null
                    ? runnable
                    : () -> AgentCapabilityScope.run(capturedCapabilitySnapshot, runnable);
            if (capturedRunContext == null) {
                capabilityScoped.run();
                return;
            }
            AgentRunScope.run(capturedRunContext, capabilityScoped);
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
