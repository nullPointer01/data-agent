package com.ai.agent.tool.governance;

/**
 * 一次逻辑工具调用的稳定结构化结果。
 *
 * @author data-agent
 */
public record AgentToolExecutionResult(
        String toolCallId,
        String toolName,
        AgentToolExecutionStatus status,
        boolean retriable,
        int attempts,
        long durationMs,
        AgentToolSafePayload payload,
        AgentToolApprovalContext approvalContext) {

    public AgentToolExecutionResult {
        toolCallId = toolCallId == null ? "" : toolCallId;
        toolName = toolName == null ? "" : toolName;
        if (status == null) {
            throw new IllegalArgumentException("工具执行状态不能为空");
        }
        payload = payload == null ? new AgentToolSafePayload("", false, false, 0) : payload;
    }

    public AgentToolExecutionResult(String toolCallId,
            String toolName,
            AgentToolExecutionStatus status,
            boolean retriable,
            int attempts,
            long durationMs,
            AgentToolSafePayload payload) {
        this(toolCallId, toolName, status, retriable, attempts, durationMs, payload, null);
    }

    public boolean successful() {
        return status == AgentToolExecutionStatus.SUCCESS;
    }

    public boolean approvalRequired() {
        return status == AgentToolExecutionStatus.APPROVAL_REQUIRED && approvalContext != null;
    }

    /**
     * 生成兼容 ToolExecutionResultMessage 的安全模型观察文本。
     *
     * @return 安全观察文本
     */
    public String toModelObservation() {
        if (successful()) {
            return payload.content();
        }
        if (status == AgentToolExecutionStatus.APPROVAL_REQUIRED) {
            return "[APPROVAL_REQUIRED toolCallId=" + toolCallId + "] " + payload.content();
        }
        String detail = payload.content().isBlank() ? "工具调用未完成" : payload.content();
        return "[TOOL_ERROR code=" + status.name() + ", toolCallId=" + toolCallId + "] " + detail;
    }
}
