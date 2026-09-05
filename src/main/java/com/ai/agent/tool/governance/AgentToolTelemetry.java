package com.ai.agent.tool.governance;

import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentEventType;
import com.ai.logging.StructuredLogger;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一记录工具 Journal、类型化事件、结构化日志和低基数指标。
 *
 * @author data-agent
 */
@Component
public class AgentToolTelemetry {

    private final MeterRegistry meterRegistry;
    private final StructuredLogger structuredLogger;

    public AgentToolTelemetry(MeterRegistry meterRegistry, StructuredLogger structuredLogger) {
        this.meterRegistry = meterRegistry;
        this.structuredLogger = structuredLogger;
    }

    public void recordAttempt(AgentToolDescriptor descriptor, String outcome) {
        meterRegistry.counter("agent_tool_attempts_total",
                "tool", metricTool(descriptor),
                "outcome", outcome).increment();
    }

    public void complete(AgentRunContext context, AgentToolInvocationContext invocationContext,
            AgentToolDescriptor descriptor, AgentToolExecutionResult result, String argumentSummary,
            boolean authorized) {
        String risk = descriptor == null ? "UNKNOWN" : descriptor.risk().name();
        meterRegistry.counter("agent_tool_calls_total",
                "tool", metricTool(descriptor),
                "status", result.status().name(),
                "risk", risk,
                "approval", descriptor != null && descriptor.approvalRequired() ? "required" : "none").increment();
        meterRegistry.timer("agent_tool_duration_seconds",
                "tool", metricTool(descriptor),
                "status", result.status().name())
                .record(Duration.ofMillis(result.durationMs()));

        AgentToolExecutionRecord record = new AgentToolExecutionRecord(
                Instant.now(),
                context == null ? "" : context.runId(),
                result.toolCallId(),
                result.toolName(),
                descriptor == null ? null : descriptor.risk(),
                result.status(),
                authorized,
                result.attempts(),
                result.durationMs(),
                argumentSummary,
                result.payload().sanitized(),
                result.payload().truncated(),
                result.attempts() > 1);
        if (context != null) {
            context.toolJournal().append(record);
            context.eventSink().emit(AgentEvent.of(context, AgentEventType.TOOL_CALL,
                    eventPayload(invocationContext, result, risk)));
        }
        structuredLogger.logGovernedToolCall(record,
                invocationContext == null ? "" : invocationContext.executorId());
    }

    private Map<String, Object> eventPayload(AgentToolInvocationContext invocationContext,
            AgentToolExecutionResult result, String risk) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", result.toolCallId());
        payload.put("toolName", result.toolName());
        payload.put("executor", invocationContext == null ? "" : invocationContext.executorId());
        payload.put("status", result.status().name());
        payload.put("risk", risk);
        payload.put("attempts", result.attempts());
        payload.put("durationMs", result.durationMs());
        payload.put("result", result.payload().content());
        payload.put("sanitized", result.payload().sanitized());
        payload.put("truncated", result.payload().truncated());
        return payload;
    }

    private String metricTool(AgentToolDescriptor descriptor) {
        return descriptor == null ? "unknown" : descriptor.name();
    }
}
