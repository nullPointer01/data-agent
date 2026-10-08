package com.ai.config;

import com.ai.agent.agentic.SupervisorExecutionScope;
import com.ai.agent.capability.AgentCapabilityBindingSnapshot;
import com.ai.agent.capability.AgentCapabilityScope;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** 捕获发起方身份、日志和 Run，异步回调执行后恢复原线程，不能借用 SDK 线程的身份。 */
public record TaskContextSnapshot(Map<String, String> mdc, Authentication authentication,
        AgentRunContext run, AgentCapabilityBindingSnapshot capabilities, boolean supervisor) {

    public static TaskContextSnapshot capture() {
        return new TaskContextSnapshot(MDC.getCopyOfContextMap(),
                SecurityContextHolder.getContext().getAuthentication(),
                AgentRunScope.current().orElse(null), AgentCapabilityScope.current().orElse(null),
                SupervisorExecutionScope.active());
    }

    public Runnable wrap(Runnable action) {
        return () -> call(() -> { action.run(); return null; });
    }

    public Executor executor(Executor delegate) {
        return action -> delegate.execute(wrap(action));
    }

    public <T> T call(Supplier<T> action) {
        var previousSecurity = SecurityContextHolder.getContext();
        var previousMdc = MDC.getCopyOfContextMap();
        try {
            var security = SecurityContextHolder.createEmptyContext();
            security.setAuthentication(authentication);
            SecurityContextHolder.setContext(security);
            setMdc(mdc);
            Supplier<T> scoped = capabilities == null ? () -> AgentCapabilityScope.without(action)
                    : () -> AgentCapabilityScope.call(capabilities, action);
            return SupervisorExecutionScope.call(supervisor,
                    () -> run == null ? AgentRunScope.without(scoped) : AgentRunScope.call(run, scoped));
        } finally {
            SecurityContextHolder.setContext(previousSecurity);
            setMdc(previousMdc);
        }
    }

    private static void setMdc(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(values);
        }
    }
}
