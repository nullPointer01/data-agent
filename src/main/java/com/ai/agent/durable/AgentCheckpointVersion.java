package com.ai.agent.durable;

/**
 * Checkpoint JSON 合同版本。
 *
 * @author data-agent
 */
public final class AgentCheckpointVersion {

    public static final int CURRENT = 1;

    private AgentCheckpointVersion() {
    }

    public static void requireSupported(int version) {
        if (version != CURRENT) {
            throw new IllegalArgumentException("不支持的 Agent Checkpoint 版本: " + version);
        }
    }
}
