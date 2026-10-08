package com.ai.agent.tool.ai;

/** 审批暂停是控制信号，必须在框架模型边界传播，不能变成普通工具返回文本。 */
public final class AgentToolSuspendedException extends RuntimeException {
    private final String approvalId;

    public AgentToolSuspendedException(String approvalId) {
        super("等待工具动作审批", null, false, false);
        this.approvalId = approvalId;
    }

    /** 返回本次已持久化的审批编号。 */
    public String approvalId() {
        return approvalId;
    }
}
