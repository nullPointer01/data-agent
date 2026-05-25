package com.ai.agent.react;

import com.ai.mcp.McpModelService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Builds ReAct prompts and calls the configured model provider.
 *
 * @author data-agent
 */
@Component
public class ReActModelCaller {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReActModelCaller.class);

    private final McpModelService mcpModelService;
    private final ReActPromptBuilder promptBuilder;

    public ReActModelCaller(McpModelService mcpModelService, ReActPromptBuilder promptBuilder) {
        this.mcpModelService = mcpModelService;
        this.promptBuilder = promptBuilder;
    }

    /**
     * Calls the model with current ReAct messages and available tool specifications.
     *
     * @param messages ReAct message history
     * @param toolSpecs available tool specifications
     * @param modelId selected model id
     * @return model response, or null when provider call fails
     */
    public String callWithTools(List<ChatMessage> messages, List<ToolSpecification> toolSpecs, String modelId) {
        try {
            String prompt = promptBuilder.buildPromptForModel(messages, toolSpecs);
            return mcpModelService.callModel(prompt, modelId);
        } catch (Exception e) {
            LOGGER.error("LLM call failed in ReAct loop", e);
            return "[模型调用失败] " + e.getMessage();
        }
    }

    /**
     * Calls the model with current ReAct messages and streams response tokens.
     *
     * @param messages ReAct message history
     * @param toolSpecs available tool specifications
     * @param modelId selected model id
     * @param tokenConsumer token consumer
     * @return full model response
     */
    public String callStreamingWithTools(List<ChatMessage> messages, List<ToolSpecification> toolSpecs,
            String modelId, Consumer<String> tokenConsumer) {
        String prompt = promptBuilder.buildPromptForModel(messages, toolSpecs);
        return mcpModelService.callModelStreaming(prompt, modelId, tokenConsumer);
    }

    /**
     * Calls the model to summarize accumulated ReAct context.
     *
     * @param messages ReAct message history
     * @param modelId selected model id
     * @param tokenConsumer token consumer
     * @return full summary response
     */
    public String callStreamingSummary(List<ChatMessage> messages, String modelId,
            Consumer<String> tokenConsumer) {
        String summaryPrompt = promptBuilder.buildSummaryPrompt(messages);
        return mcpModelService.callModelStreaming(summaryPrompt, modelId, tokenConsumer);
    }
}
