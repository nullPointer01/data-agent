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

    /** 跨 Future、框架代理及反射边界找回类型化暂停；限制深度防止异常链成环。 */
    public static AgentToolSuspendedException find(Throwable error) {
        for (int depth = 0; error != null && depth < 16; depth++, error = error.getCause()) {
            if (error instanceof AgentToolSuspendedException suspended) return suspended;
        }
        return null;
    }
}
