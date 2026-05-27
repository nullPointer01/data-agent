package com.ai.agent.react;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.ToolResult;

/**
 * Handles one ReAct model response by parsing optional tool calls and appending observations.
 *
 * @author data-agent
 */
@Component
public class ReActStepHandler {

    private static final String MEMORY_INDEXED_MARKER = "__MEMORY_INDEXED__";
    private static final String MEMORY_INDEXED_OBSERVATION = "记忆已保存。请继续回答用户的问题。";
    private static final String TOOL_RESULT_INSTRUCTION =
            "工具执行结果:\n%s\n\n请根据结果继续分析或给出最终回答。如果已有足够信息，请直接给出最终回答（使用Markdown格式），不要再调用工具。";

    private final ReActResponseParser responseParser;
    private final AgentToolInvoker toolInvoker;
    private final ErrorRecoveryAdvisor recoveryAdvisor;

    @Autowired
    public ReActStepHandler(ReActResponseParser responseParser, AgentToolInvoker toolInvoker,
            @Nullable ErrorRecoveryAdvisor recoveryAdvisor) {
        this.responseParser = responseParser;
        this.toolInvoker = toolInvoker;
        this.recoveryAdvisor = recoveryAdvisor;
    }

    /**
     * Handles one model response and mutates message history when a tool observation is produced.
     *
     * @param llmResponse raw model response
     * @param messages mutable ReAct message history
     * @return step outcome
     */
    public ReActStepOutcome handle(String llmResponse, List<ChatMessage> messages) {
        ReActToolCall toolCall = responseParser.parseToolCall(llmResponse);
        if (toolCall == null) {
            return ReActStepOutcome.finalAnswer(responseParser.cleanAssistantAnswer(llmResponse));
        }

        String toolCallResult = toolInvoker.invoke(toolCall);
        if (MEMORY_INDEXED_MARKER.equals(toolCallResult)) {
            // Memory indexing 是一种内部副作用；通知模型继续而不计入新的迭代。
            messages.add(AiMessage.from(llmResponse));
            messages.add(UserMessage.from(MEMORY_INDEXED_OBSERVATION));
            return ReActStepOutcome.memoryIndexed();
        }

        // 如果配置了错误恢复建议器，则先尝试追加恢复建议并获取建议对象
        ErrorRecoveryAdvice advice = recoveryAdvisor != null ? recoveryAdvisor.advise(toolCall.name(), toolCallResult)
                : ErrorRecoveryAdvice.none();
        String toolResultWithAdvice = recoveryAdvisor != null ? recoveryAdvisor.appendAdvice(toolCall.name(), toolCallResult)
                : toolCallResult;

        messages.add(AiMessage.from(llmResponse));
        messages.add(UserMessage.from(TOOL_RESULT_INSTRUCTION.formatted(toolResultWithAdvice)));

        // 当存在需要恢复的建议时，添加一条用户提示以便模型能读取到“错误恢复建议”段落
        if (advice != null && advice.recoveryRequired() && advice.hasInstruction()) {
            messages.add(UserMessage.from("错误恢复建议: " + advice.instruction()));
        }

        return ReActStepOutcome.toolObservation(toolCall.name(), toolCallResult, isFinalAnswerRequired(toolCallResult), advice);
    }

    private boolean isFinalAnswerRequired(String toolResult) {
        if (toolResult == null) {
            return true;
        }
        return toolResult.startsWith("需要用户输入:")
                || toolResult.contains("不存在")
                || toolResult.contains("没有已上传")
                || toolResult.contains("没有可用的");
    }
}
