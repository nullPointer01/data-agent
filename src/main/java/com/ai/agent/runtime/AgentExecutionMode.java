package com.ai.agent.runtime;

import java.util.Locale;

/**
 * Agent Harness 支持的执行模式。
 *
 * @author data-agent
 */
public enum AgentExecutionMode {

    CHAT,
    REACT,
    ORCHESTRATED;

    /**
     * 将配置文本解析为执行模式。
     *
     * @param value 配置值
     * @return 执行模式
     */
    public static AgentExecutionMode fromCode(String value) {
        if (value == null || value.isBlank()) {
            return REACT;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "CHAT" -> CHAT;
            case "REACT" -> REACT;
            case "ORCHESTRATED", "ORCHESTRATOR" -> ORCHESTRATED;
            default -> throw new IllegalArgumentException("不支持的 Agent 执行模式: " + value);
        };
    }
}
