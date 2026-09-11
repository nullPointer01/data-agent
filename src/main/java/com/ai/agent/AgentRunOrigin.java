package com.ai.agent;

/**
 * 标识一次 Agent 执行由哪个业务入口发起，并声明是否允许产生对话记忆副作用。
 *
 * @author data-agent
 */
public enum AgentRunOrigin {

    INTERACTIVE(true),
    DELEGATED(false),
    DURABLE_RESUME(false);

    private final boolean conversationPersistenceEnabled;

    AgentRunOrigin(boolean conversationPersistenceEnabled) {
        this.conversationPersistenceEnabled = conversationPersistenceEnabled;
    }

    public boolean isConversationPersistenceEnabled() {
        return conversationPersistenceEnabled;
    }
}
