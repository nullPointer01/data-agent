package com.ai.agent.react;

import java.util.List;

/**
 * 从 ReAct 模型响应中解析出的工具调用。
 *
 * @param name 工具名称
 * @param rawArguments 原始参数文本
 * @param arguments 结构化参数
 * @author data-agent
 */
public record ReActToolCall(String name, String rawArguments, List<String> arguments) {

    public ReActToolCall {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public boolean hasArguments(int count) {
        return arguments.size() >= count;
    }

    public String argument(int index) {
        return arguments.get(index);
    }
}
