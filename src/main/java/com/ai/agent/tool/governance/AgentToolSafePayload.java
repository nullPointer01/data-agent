package com.ai.agent.tool.governance;

/**
 * 已完成脱敏和截断、可交给模型与可观测系统的唯一工具载荷。
 *
 * @param content 安全文本
 * @param sanitized 是否发生脱敏
 * @param truncated 是否发生截断
 * @param originalLength 原始字符数
 * @author data-agent
 */
public record AgentToolSafePayload(
        String content,
        boolean sanitized,
        boolean truncated,
        int originalLength) {

    public AgentToolSafePayload {
        content = content == null ? "" : content;
    }
}
