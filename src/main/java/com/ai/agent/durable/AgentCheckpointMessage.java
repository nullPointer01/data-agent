package com.ai.agent.durable;

/**
 * 不依赖 LangChain4j 实现类型的可移植消息。
 *
 * @param role 消息角色
 * @param content 文本内容
 * @param toolCallId 工具调用编号
 * @param toolName 工具名称
 * @param argumentsJson 工具参数 JSON，仅存在于整体加密的 Checkpoint 中
 * @author data-agent
 */
public record AgentCheckpointMessage(
        Role role,
        String content,
        String toolCallId,
        String toolName,
        String argumentsJson) {

    public AgentCheckpointMessage {
        if (role == null) {
            throw new IllegalArgumentException("Checkpoint message role 不能为空");
        }
        content = content == null ? "" : content;
    }

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }
}
