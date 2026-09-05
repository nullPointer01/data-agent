package com.ai.agent.tool;

/**
 * 工具执行结果，替代纯字符串协议实现类型安全的成功/失败判断。
 *
 * @param success 是否执行成功
 * @param payload 执行结果文本
 * @author data-agent
 */
public record ToolResult(boolean success, String payload) {

    public static ToolResult ok(String payload) {
        return new ToolResult(true, payload);
    }

    public static ToolResult fail(String payload) {
        return new ToolResult(false, payload);
    }
}
