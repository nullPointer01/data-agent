package com.ai.agent.react;

import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.memory.dto.MemoryContext;
import com.ai.mcp.McpModelService;
import dev.langchain4j.model.input.PromptTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 供 ReAct 快速路径使用的直答模型调用器。
 *
 * @author data-agent
 */
@Service
public class ReActFastAnswerService {

    private static final String MODEL_FAILURE_MESSAGE = "模型调用失败，请稍后重试";
    private static final PromptTemplate FAST_PATH_PROMPT_TEMPLATE = PromptTemplate.from("""
            你是 Data Agent 的企业级智能助手。
            当前请求被判定为简单直答，不需要调用工具。

            回答要求：
            - 直接回答用户问题
            - 不编造业务数据
            - 如果问题需要数据、文件、知识库或实时查询，请说明需要进入分析模式
            - 使用简洁中文

            {{memorySection}}

            用户问题：
            {{question}}
            """);

    private final McpModelService mcpModelService;
    private final MemoryContextPromptFormatter memoryContextPromptFormatter;

    public ReActFastAnswerService(McpModelService mcpModelService,
            MemoryContextPromptFormatter memoryContextPromptFormatter) {
        this.mcpModelService = mcpModelService;
        this.memoryContextPromptFormatter = memoryContextPromptFormatter;
    }

    /**
     * 只调用一次模型生成直答。
     *
     * @param question 用户问题
     * @param modelId 选中的模型 ID
     * @param memoryContext 记忆上下文
     * @return 直答结果
     */
    public String answer(String question, String modelId, MemoryContext memoryContext) {
        String result = mcpModelService.callModel(buildPrompt(question, memoryContext), modelId);
        if (!StringUtils.hasText(result)) {
            return MODEL_FAILURE_MESSAGE;
        }
        return result.trim();
    }

    public String answer(String question, String modelId) {
        return answer(question, modelId, MemoryContext.empty());
    }

    /**
     * 流式输出直答 token。
     *
     * @param question 用户问题
     * @param modelId 选中的模型 ID
     * @param memoryContext 记忆上下文
     * @param tokenConsumer token 消费器
     * @return 完整直答
     */
    public String answerStreaming(String question,
            String modelId,
            MemoryContext memoryContext,
            Consumer<String> tokenConsumer) {
        String result = mcpModelService.callModelStreaming(buildPrompt(question, memoryContext), modelId, tokenConsumer);
        if (!StringUtils.hasText(result)) {
            return MODEL_FAILURE_MESSAGE;
        }
        return result.trim();
    }

    public String answerStreaming(String question, String modelId, Consumer<String> tokenConsumer) {
        return answerStreaming(question, modelId, MemoryContext.empty(), tokenConsumer);
    }

    private String buildPrompt(String question, MemoryContext memoryContext) {
        return FAST_PATH_PROMPT_TEMPLATE.apply(Map.of(
                "memorySection", memoryContextPromptFormatter.toSection(memoryContext),
                "question", question)).text();
    }
}
