package com.ai.agent.runtime;

/**
 * 在运行边界拒绝继续执行时抛出的轻量异常。
 *
 * @author data-agent
 */
public class AgentRunTerminatedException extends RuntimeException {

    private final AgentRunSnapshot snapshot;

    public AgentRunTerminatedException(AgentRunSnapshot snapshot) {
        super(snapshot == null ? "Agent Run 已终止" : snapshot.detail());
        this.snapshot = snapshot;
    }

    public AgentRunSnapshot getSnapshot() {
        return snapshot;
    }
}
