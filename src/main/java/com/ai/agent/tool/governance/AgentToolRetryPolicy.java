package com.ai.agent.tool.governance;

import com.ai.agent.runtime.AgentRunContext;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 工具内部重试的安全四联条件和有界退避策略。
 *
 * @author data-agent
 */
@Component
public class AgentToolRetryPolicy {

    private final AgentToolGovernanceProperties properties;

    public AgentToolRetryPolicy(AgentToolGovernanceProperties properties) {
        this.properties = properties;
    }

    public int maximumAttempts(AgentToolDescriptor descriptor) {
        if (!descriptor.readOnly() || !descriptor.idempotent() || !descriptor.retryable()) {
            return 1;
        }
        return Math.min(descriptor.maxAttempts(), properties.getMaxAttempts());
    }

    public boolean canRetry(AgentToolDescriptor descriptor, AgentToolFailure failure, int completedAttempts,
            AgentRunContext runContext) {
        if (completedAttempts >= maximumAttempts(descriptor)
                || !descriptor.readOnly()
                || !descriptor.idempotent()
                || !descriptor.retryable()
                || failure == null
                || !failure.retriable()
                || runContext == null) {
            return false;
        }
        try {
            runContext.control().ensureActive();
            return !runContext.control().remainingTime().isZero();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public Duration backoffAfter(int completedAttempts) {
        long multiplier = 1L << Math.min(Math.max(0, completedAttempts - 1), 20);
        long requestedMillis;
        try {
            requestedMillis = Math.multiplyExact(properties.getInitialBackoff().toMillis(), multiplier);
        } catch (ArithmeticException e) {
            requestedMillis = Long.MAX_VALUE;
        }
        return Duration.ofMillis(Math.min(requestedMillis, properties.getMaxBackoff().toMillis()));
    }
}
