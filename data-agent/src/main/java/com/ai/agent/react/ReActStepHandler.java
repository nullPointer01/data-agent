package com.ai.agent.react;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.input.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import com.ai.agent.tool.AgentToolInvoker;

/**
 * 处理单次 ReAct 模型响应：消费原生 Function Calling 工具请求并回填观察结果。
 *
 * @author data-agent
 */
@Component
public class ReActStepHandler {

    private static final String MEMORY_INDEXED_MARKER = "__MEMORY_INDEXED__";
    private static final String MEMORY_INDEXED_OBSERVATION = "记忆已保存。请继续回答用户的问题。";
    private static final PromptTemplate TOOL_RESULT_TEMPLATE = PromptTemplate.from(
            "工具执行结果:\n{{result}}\n\n请根据结果继续分析或给出最终回答。如果已有足够信息，请直接给出最终回答（使用Markdown格式），不要再调用工具。");

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
     * 处理一次原生模型响应：有工具请求则执行并回填观察，否则视为最终回答。
     *
     * @param aiMessage 模型返回的 AI 消息
     * @param messages 可变的 ReAct 消息历史
     * @return 步骤处理结果
     */
    public ReActStepOutcome handleNative(AiMessage aiMessage, List<ChatMessage> messages) {
        if (aiMessage == null) {
            return ReActStepOutcome.finalAnswer("");
        }
        if (!aiMessage.hasToolExecutionRequests()) {
            String text = aiMessage.text() == null ? "" : aiMessage.text();
            return ReActStepOutcome.finalAnswer(responseParser.cleanAssistantAnswer(text));
        }

        // OpenAI 协议要求每个工具请求都必须有对应的 tool 响应，否则下一轮调用会被厂商拒绝，
        // 因此并行工具调用时必须全部执行并逐个回填
        messages.add(aiMessage);
        String firstToolName = null;
        String firstToolResult = null;
        ErrorRecoveryAdvice firstAdvice = null;
        boolean allMemoryIndexed = true;

        for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
            String toolCallResult = toolInvoker.invoke(request);
            if (MEMORY_INDEXED_MARKER.equals(toolCallResult)) {
                // Memory indexing 是一种内部副作用；通知模型继续而不计入新的迭代。
                messages.add(ToolExecutionResultMessage.from(request, MEMORY_INDEXED_OBSERVATION));
                continue;
            }
            allMemoryIndexed = false;

            // 如果配置了错误恢复建议器，则先尝试追加恢复建议并获取建议对象
            ErrorRecoveryAdvice advice = recoveryAdvisor != null
                    ? recoveryAdvisor.advise(request.name(), toolCallResult)
                    : ErrorRecoveryAdvice.none();
            String toolResultWithAdvice = recoveryAdvisor != null
                    ? recoveryAdvisor.appendAdvice(request.name(), toolCallResult)
                    : toolCallResult;
            messages.add(ToolExecutionResultMessage.from(request,
                    TOOL_RESULT_TEMPLATE.apply(Map.of("result", toolResultWithAdvice)).text()));
            if (advice != null && advice.recoveryRequired() && advice.hasInstruction()) {
                messages.add(UserMessage.from("错误恢复建议: " + advice.instruction()));
            }
            if (firstToolName == null) {
                firstToolName = request.name();
                firstToolResult = toolCallResult;
                firstAdvice = advice;
            }
        }

        if (allMemoryIndexed) {
            return ReActStepOutcome.memoryIndexed();
        }
        // 步骤语义（思考步骤展示、是否需要终答）沿用第一个工具，与单工具行为一致
        return ReActStepOutcome.toolObservation(firstToolName, firstToolResult,
                isFinalAnswerRequired(firstToolResult), firstAdvice);
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
