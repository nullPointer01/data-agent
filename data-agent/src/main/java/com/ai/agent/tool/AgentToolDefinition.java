package com.ai.agent.tool;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.function.Function;
import com.ai.agent.react.ReActToolCall;

/**
 * Definition of one ReAct tool exposed to the model.
 *
 * @param name tool name used in CALL syntax
 * @param description model-facing tool description
 * @param executor tool executor
 * @author data-agent
 */
record AgentToolDefinition(String name, String description, Function<ReActToolCall, String> executor) {

    /**
     * Converts this definition to a LangChain4j tool specification.
     *
     * @return tool specification
     */
    ToolSpecification toSpecification() {
        return ToolSpecification.builder()
                .name(name)
                .description(description)
                .build();
    }
}
