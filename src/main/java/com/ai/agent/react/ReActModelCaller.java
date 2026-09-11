package com.ai.agent.react;

import com.ai.agent.context.AgentContextGovernor.ContextLimitException;
import com.ai.agent.runtime.planning.AgentToolChoice;
import com.ai.mcp.McpModelService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 以 LangChain4j 原生消息协议调用已配置的模型服务。
 *
 * <p>消息角色结构与工具规格原样传给厂商（原生 Function Calling），
 * 不再拍平为单条提示词文本。</p>
 *
 * @author data-agent
 */
@Component
public class ReActModelCaller {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReActModelCaller.class);

    private final McpModelService mcpModelService;

    public ReActModelCaller(McpModelService mcpModelService) {
        this.mcpModelService = mcpModelService;
    }

    /**
     * 使用当前 ReAct 消息和可用工具规格调用模型。
     *
     * @param messages ReAct 消息历史
     * @param toolSpecs 可用工具规格
     * @param modelId 选中的模型 ID
     * @return 模型响应（含工具调用请求与 token 用量）；服务调用失败时返回 null
     */
    public ChatResponse callWithTools(List<ChatMessage> messages, List<ToolSpecification> toolSpecs,
            String modelId) {
        return callWithTools(messages, toolSpecs, modelId, AgentToolChoice.AUTO);
    }

    /**
     * 使用指定工具选择约束调用模型。
     *
     * @param messages ReAct 消息历史
     * @param toolSpecs 可用工具规格
     * @param modelId 选中的模型 ID
     * @param toolChoice 当前模型轮次的工具选择约束
     * @return 模型响应
     */
    public ChatResponse callWithTools(List<ChatMessage> messages, List<ToolSpecification> toolSpecs,
            String modelId, AgentToolChoice toolChoice) {
        try {
            ToolChoice modelToolChoice = toolChoice == AgentToolChoice.REQUIRED
                    ? ToolChoice.REQUIRED : ToolChoice.AUTO;
            return mcpModelService.callMessages(messages, toolSpecs, modelId, modelToolChoice);
        } catch (ContextLimitException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("ReAct 循环中 LLM 调用失败", e);
            return null;
        }
    }

    /**
     * 使用当前 ReAct 消息调用模型并流式输出文本 token，工具调用请求在完整响应中返回。
     *
     * @param messages ReAct 消息历史
     * @param toolSpecs 可用工具规格
     * @param modelId 选中的模型 ID
     * @param tokenConsumer token 消费者
     * @return 完整模型响应；服务调用失败时返回 null
     */
    public ChatResponse callStreamingWithTools(List<ChatMessage> messages, List<ToolSpecification> toolSpecs,
            String modelId, Consumer<String> tokenConsumer) {
        return mcpModelService.callMessagesStreaming(messages, toolSpecs, modelId, tokenConsumer);
    }

    /**
     * 调用模型对已累积的 ReAct 上下文进行总结（无工具）。
     *
     * @param messages ReAct 消息历史
     * @param modelId 选中的模型 ID
     * @param tokenConsumer token 消费者
     * @return 完整总结文本；调用失败时返回 null
     */
    public String callStreamingSummary(List<ChatMessage> messages, String modelId,
            Consumer<String> tokenConsumer) {
        ChatResponse response = mcpModelService.callMessagesStreaming(messages, null, modelId, tokenConsumer);
        if (response == null || response.aiMessage() == null) {
            return null;
        }
        return response.aiMessage().text();
    }
}
