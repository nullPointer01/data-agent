package com.ai.agent.tool.governance;

import com.ai.agent.runtime.AgentRunContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * 工具准入结果。底层执行器只在 admitted 且无需等待审批时可被调用。
 *
 * @param status 准入状态
 * @param admittedToolCall 已校验的内部调用
 * @param safeMessage 可展示的拒绝或等待说明
 * @author data-agent
 */
public record AgentToolAdmission(
        AgentToolExecutionStatus status,
        AdmittedToolCall admittedToolCall,
        String safeMessage) {

    public boolean admitted() {
        return status == AgentToolExecutionStatus.SUCCESS && admittedToolCall != null;
    }

    public boolean approvalRequired() {
        return status == AgentToolExecutionStatus.APPROVAL_REQUIRED && admittedToolCall != null;
    }

    public record AdmittedToolCall(
            ToolExecutionRequest request,
            AgentRunContext runContext,
            AgentToolInvocationContext invocationContext,
            AgentToolRegistry.RegisteredTool registeredTool,
            AgentToolArgumentValidation validation,
            String toolCallId) {

        public AgentToolDescriptor descriptor() {
            return registeredTool.descriptor();
        }
    }
}
