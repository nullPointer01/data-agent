package com.ai.agent.tool.governance;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 模型工具参数的结构化校验结果。
 *
 * @param valid 是否有效
 * @param arguments 解析后的顶层对象
 * @param safeSummary 不含参数原值的摘要
 * @param error 安全错误说明
 * @author data-agent
 */
public record AgentToolArgumentValidation(
        boolean valid,
        ObjectNode arguments,
        String safeSummary,
        String error) {

    public static AgentToolArgumentValidation valid(ObjectNode arguments, String safeSummary) {
        return new AgentToolArgumentValidation(true, arguments, safeSummary, "");
    }

    public static AgentToolArgumentValidation invalid(String safeSummary, String error) {
        return new AgentToolArgumentValidation(false, null, safeSummary, error);
    }
}
