package com.ai.agent.react;

import com.ai.model.AnalysisResponse;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import com.ai.agent.tool.governance.AgentToolInvocationContext;

/**
 * 处理一轮同步 ReAct 模型响应。
 *
 * @author data-agent
 */
@Component
public class ReActSynchronousStepProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReActSynchronousStepProcessor.class);
    private static final int LOG_RESPONSE_PREVIEW_LENGTH = 200;
    private static final int TOOL_RESULT_PREVIEW_LENGTH = 300;
    private static final String SUMMARY_INTRODUCTION = "基于以上工具返回的结果，我来整合总结：";

    private final ReActModelCaller modelCaller;
    private final ReActResponseParser responseParser;
    private final ReActStepHandler stepHandler;
    private final SelfReflector selfReflector;

    public ReActSynchronousStepProcessor(ReActModelCaller modelCaller,
            ReActResponseParser responseParser,
            ReActStepHandler stepHandler,
            SelfReflector selfReflector) {
        this.modelCaller = modelCaller;
        this.responseParser = responseParser;
        this.stepHandler = stepHandler;
        this.selfReflector = selfReflector;
    }

    /**
     * 处理一轮模型响应，并更新答案、消息和可见思考步骤。
     *
     * @param aiMessage 模型返回的 AI 消息（可能携带原生工具调用请求）
     * @param toolSpecs 可用的工具描述
     * @param modelId 选中的模型 ID
     * @param messages 可变的 ReAct 消息历史
     * @param finalAnswer 最终回答累加器
     * @param thinkingSteps 可见思考步骤
     * @param iteration 当前循环次数
     * @param recoveryTracker 本次运行的恢复跟踪器
     * @return 循环控制结果
     */
    public ReActLoopStepResult process(AiMessage aiMessage,
            List<ToolSpecification> toolSpecs,
            String modelId,
            List<ChatMessage> messages,
            StringBuilder finalAnswer,
            List<AnalysisResponse.ThinkingStep> thinkingSteps,
            int iteration,
            ReActRecoveryTracker recoveryTracker,
            AgentToolInvocationContext invocationContext) {
        String llmResponse = aiMessage.text() == null ? "" : aiMessage.text();
        LOGGER.info("ReAct LLM response (first 200): {}", preview(llmResponse, LOG_RESPONSE_PREVIEW_LENGTH));
        addThinkingStep(llmResponse, thinkingSteps, iteration);

        ReActStepOutcome outcome = stepHandler.handleNative(aiMessage, messages, invocationContext);
        if (outcome.isFinalAnswer()) {
            finalAnswer.append(outcome.answer());
            thinkingSteps.add(new AnalysisResponse.ThinkingStep(iteration, "answer", "生成最终回答"));
            addFinalReflection(thinkingSteps, iteration, finalAnswer);
            return new ReActLoopStepResult(false, false);
        }
        if (outcome.isMemoryIndexed()) {
            return new ReActLoopStepResult(true, true);
        }
        if (outcome.isApprovalRequired()) {
            return ReActLoopStepResult.suspended(outcome.approvalId());
        }

        addToolStep(thinkingSteps, outcome, iteration);
        ReActRecoveryDecision recoveryDecision = addReflectionStep(thinkingSteps, outcome, iteration, recoveryTracker);
        if (recoveryDecision.recoveryRequired() && !recoveryDecision.allowed()) {
            return appendFallbackAnswer(outcome, messages, toolSpecs, modelId, finalAnswer, thinkingSteps, iteration);
        }
        boolean finalAnswerReady = appendFinalAnswerIfReady(outcome, messages, toolSpecs, modelId,
                finalAnswer, thinkingSteps, iteration);
        return new ReActLoopStepResult(!finalAnswerReady, false, recoveryDecision.recoveryRequired());
    }

    private void addThinkingStep(String llmResponse, List<AnalysisResponse.ThinkingStep> thinkingSteps,
            int iteration) {
        String thinking = responseParser.extractThinking(llmResponse);
        if (thinking != null) {
            thinkingSteps.add(new AnalysisResponse.ThinkingStep(iteration, "thinking", thinking));
        }
    }

    private void addToolStep(List<AnalysisResponse.ThinkingStep> thinkingSteps, ReActStepOutcome outcome,
            int iteration) {
        AnalysisResponse.ThinkingStep toolStep = new AnalysisResponse.ThinkingStep(iteration, "tool_call", "调用工具");
        toolStep.setToolName(outcome.toolName());
        toolStep.setToolResult(preview(outcome.toolResult(), TOOL_RESULT_PREVIEW_LENGTH));
        thinkingSteps.add(toolStep);
    }

    private ReActRecoveryDecision addReflectionStep(List<AnalysisResponse.ThinkingStep> thinkingSteps,
            ReActStepOutcome outcome, int iteration, ReActRecoveryTracker recoveryTracker) {
        ReActRecoveryDecision decision = recoveryTracker.recordAttempt(outcome.recoveryAdvice());
        if (!decision.recoveryRequired()) {
            return decision;
        }
        String reflection = decision.allowed()
                ? selfReflector.reflect(outcome, decision.attempt())
                : selfReflector.reflectExhausted(outcome);
        if (reflection.isBlank()) {
            return decision;
        }
        thinkingSteps.add(new AnalysisResponse.ThinkingStep(iteration, "reflection", reflection));
        return decision;
    }

    private ReActLoopStepResult appendFallbackAnswer(ReActStepOutcome outcome, List<ChatMessage> messages,
            List<ToolSpecification> toolSpecs, String modelId, StringBuilder finalAnswer,
            List<AnalysisResponse.ThinkingStep> thinkingSteps, int iteration) {
        messages.add(AiMessage.from("错误恢复次数已达到上限，请停止重试并给出保守结论。"));
        String summaryText = summaryText(modelCaller.callWithTools(messages, toolSpecs, modelId));
        if (summaryText != null) {
            finalAnswer.append(responseParser.cleanAssistantAnswer(summaryText));
        } else {
            finalAnswer.append(outcome.toolResult());
        }
        thinkingSteps.add(new AnalysisResponse.ThinkingStep(iteration, "answer", "错误恢复达到上限，生成降级回答"));
        addFinalReflection(thinkingSteps, iteration, finalAnswer);
        return new ReActLoopStepResult(false, false, false);
    }

    private boolean appendFinalAnswerIfReady(ReActStepOutcome outcome, List<ChatMessage> messages,
            List<ToolSpecification> toolSpecs, String modelId, StringBuilder finalAnswer,
            List<AnalysisResponse.ThinkingStep> thinkingSteps, int iteration) {
        if (!outcome.finalAnswerRequired()) {
            return false;
        }
        messages.add(AiMessage.from(SUMMARY_INTRODUCTION));
        String summaryText = summaryText(modelCaller.callWithTools(messages, toolSpecs, modelId));
        if (summaryText != null) {
            finalAnswer.append(responseParser.cleanAssistantAnswer(summaryText));
        } else {
            finalAnswer.append(outcome.toolResult());
        }
        thinkingSteps.add(new AnalysisResponse.ThinkingStep(iteration, "answer", "整合结果生成最终回答"));
        addFinalReflection(thinkingSteps, iteration, finalAnswer);
        return true;
    }

    private String summaryText(ChatResponse response) {
        if (response == null || response.aiMessage() == null) {
            return null;
        }
        return response.aiMessage().text();
    }

    private void addFinalReflection(List<AnalysisResponse.ThinkingStep> thinkingSteps, int iteration,
            StringBuilder finalAnswer) {
        String reflection = selfReflector.reflectFinalAnswer(finalAnswer.toString(), thinkingSteps);
        if (!reflection.isBlank()) {
            thinkingSteps.add(new AnalysisResponse.ThinkingStep(iteration, "reflection", reflection));
        }
    }

    private String preview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }
}
