package com.ai.agent.tool.governance;

import java.util.Set;

/**
 * 一次具体 Agent 执行者能够调用的精确工具白名单。
 *
 * @param executorId 执行者标识
 * @param allowedToolNames 服务端构造的工具白名单
 * @author data-agent
 */
public record AgentToolInvocationContext(String executorId, Set<String> allowedToolNames) {

    public AgentToolInvocationContext {
        if (executorId == null || executorId.isBlank()) {
            throw new IllegalArgumentException("工具调用执行者不能为空");
        }
        allowedToolNames = allowedToolNames == null ? Set.of() : Set.copyOf(allowedToolNames);
    }

    public boolean allows(String toolName) {
        return allowedToolNames.contains(toolName);
    }
}
