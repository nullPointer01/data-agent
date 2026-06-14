package com.ai.agent.react;

import com.ai.mcp.McpModelService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.Response;
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
    private final ReActPromptBuilder promptBuilder;

    public ReActModelCaller(McpModelService mcpModelService, ReActPromptBuilder promptBuilder) {
        this.mcpModelService = mcpModelService;
        this.promptBuilder = promptBuilder;
    }

    /**
     * 使用当前 ReAct 消息和可用工具规格调用模型。
     *
     * @param messages ReAct 消息历史
     * @param toolSpecs 可用工具规格
     * @param modelId 选中的模型 ID
     * @return 模型响应（含工具调用请求与 token 用量）；服务调用失败时返回 null
     */
    public Response<AiMessage> callWithTools(List<ChatMessage> messages, List<ToolSpecification> toolSpecs,
            String modelId) {
        try {
            List<ChatMessage> prepared = promptBuilder.prepareNativeMessages(messages);
            return mcpModelService.callMessages(prepared, toolSpecs, modelId);
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
    public Response<AiMessage> callStreamingWithTools(List<ChatMessage> messages, List<ToolSpecification> toolSpecs,
            String modelId, Consumer<String> tokenConsumer) {
        List<ChatMessage> prepared = promptBuilder.prepareNativeMessages(messages);
        return mcpModelService.callMessagesStreaming(prepared, toolSpecs, modelId, tokenConsumer);
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
        List<ChatMessage> prepared = promptBuilder.prepareNativeMessages(messages);
        Response<AiMessage> response = mcpModelService.callMessagesStreaming(prepared, null, modelId, tokenConsumer);
        if (response == null || response.content() == null) {
            return null;
        }
        return response.content().text();
    }
}
