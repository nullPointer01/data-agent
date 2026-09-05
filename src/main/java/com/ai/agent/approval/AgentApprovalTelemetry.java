package com.ai.agent.approval;

import com.ai.logging.StructuredLogger;
import com.ai.service.AuditLogService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一输出审批、恢复和沙箱动作的安全审计、结构化日志与低基数指标。
 *
 * @author data-agent
 */
@Component
public class AgentApprovalTelemetry {

    private static final String RESOURCE_TYPE = "AGENT_TOOL_APPROVAL";

    private final AuditLogService auditLogService;
    private final MeterRegistry meterRegistry;
    private final StructuredLogger structuredLogger;

    public AgentApprovalTelemetry(AuditLogService auditLogService,
            MeterRegistry meterRegistry,
            StructuredLogger structuredLogger) {
        this.auditLogService = auditLogService;
        this.meterRegistry = meterRegistry;
        this.structuredLogger = structuredLogger;
    }

    public void record(String event,
            String outcome,
            String tenantId,
            String actorUserId,
            String actorName,
            String approvalId,
            String runId,
            String toolCallId,
            String toolName) {
        String normalizedEvent = normalize(event, "UNKNOWN");
        String normalizedOutcome = normalize(outcome, "UNKNOWN");
        String normalizedTool = normalize(toolName, "unknown");
        String action = "AGENT_APPROVAL_" + normalizedEvent;
        String message = "runId=" + safe(runId)
                + ", toolCallId=" + safe(toolCallId)
                + ", tool=" + normalizedTool;
        auditLogService.record(
                action,
                RESOURCE_TYPE,
                approvalId,
                normalizedOutcome,
                message,
                tenantId,
                actorUserId,
                actorName,
                null);
        meterRegistry.counter(
                "agent_approval_events_total",
                "event", normalizedEvent,
                "outcome", normalizedOutcome,
                "tool", normalizedTool).increment();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", normalizedEvent);
        payload.put("outcome", normalizedOutcome);
        payload.put("approvalId", safe(approvalId));
        payload.put("runId", safe(runId));
        payload.put("toolCallId", safe(toolCallId));
        payload.put("toolName", normalizedTool);
        structuredLogger.logEvent("AGENT_APPROVAL", payload);
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
