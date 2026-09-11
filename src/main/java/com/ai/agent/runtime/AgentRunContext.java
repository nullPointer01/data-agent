package com.ai.agent.runtime;

import com.ai.agent.AgentExecutionContext;
import com.ai.agent.outcome.AgentTaskContract;
import com.ai.agent.runtime.event.AgentEventSink;
import com.ai.agent.tool.governance.AgentToolAuthorizationSnapshot;
import com.ai.agent.tool.governance.AgentToolExecutionJournal;

/**
 * 一次 Agent 执行使用的不可变上下文。
 *
 * @param runId 运行编号
 * @param tenantId 租户编号
 * @param userId 用户编号
 * @param sessionId 会话编号
 * @param agentId Agent 编号
 * @param mode 执行模式
 * @param executionContext 已准备的业务执行上下文
 * @param control 运行控制器
 * @param eventSink 统一事件接收端
 * @param toolAuthorization 工具权限快照
 * @param toolJournal 有界工具执行 Journal
 * @param taskContract 创建 Run 时固化的可选任务合同
 * @author data-agent
 */
public record AgentRunContext(
        String runId,
        String tenantId,
        String userId,
        String sessionId,
        String agentId,
        AgentExecutionMode mode,
        AgentExecutionContext executionContext,
        AgentRunControl control,
        AgentEventSink eventSink,
        AgentToolAuthorizationSnapshot toolAuthorization,
        AgentToolExecutionJournal toolJournal,
        AgentTaskContract taskContract) {

    /**
     * 从准备完成的执行上下文捕获任务合同，兼容现有 Run 构造调用。
     */
    public AgentRunContext(String runId,
            String tenantId,
            String userId,
            String sessionId,
            String agentId,
            AgentExecutionMode mode,
            AgentExecutionContext executionContext,
            AgentRunControl control,
            AgentEventSink eventSink,
            AgentToolAuthorizationSnapshot toolAuthorization,
            AgentToolExecutionJournal toolJournal) {
        this(runId, tenantId, userId, sessionId, agentId, mode, executionContext, control, eventSink,
                toolAuthorization, toolJournal,
                executionContext == null || executionContext.getRequest() == null
                        ? null : executionContext.getRequest().getTaskContract());
    }

    public AgentRunContext {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("Agent Run ID 不能为空");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Agent Run mode 不能为空");
        }
        if (executionContext == null) {
            throw new IllegalArgumentException("Agent executionContext 不能为空");
        }
        if (control == null) {
            throw new IllegalArgumentException("Agent Run control 不能为空");
        }
        if (eventSink == null) {
            throw new IllegalArgumentException("Agent Run eventSink 不能为空");
        }
        if (toolAuthorization == null || toolJournal == null) {
            throw new IllegalArgumentException("Agent Run 工具治理上下文不能为空");
        }
    }

    /**
     * 获取当前运行快照。
     *
     * @return 运行快照
     */
    public AgentRunSnapshot snapshot() {
        return control.snapshot();
    }
}
