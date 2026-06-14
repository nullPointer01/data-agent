package com.ai.agent.react;

import com.ai.model.AnalysisResponse;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 处理一轮流式 ReAct 模型响应。
 *
 * @author data-agent
 */
@Component
public class ReActStreamingStepProcessor {

    private static final int TOOL_RESULT_PREVIEW_LENGTH = 300;
    private static final String SUMMARY_INTRODUCTION = "基于以上工具返回的结果，我来整合总结：";

    private final ReActModelCaller modelCaller;
    private final ReActResponseParser responseParser;
    private final ReActStepHandler stepHandler;
    private final ReActStreamEventWriter streamEventWriter;
    private final SelfReflector selfReflector;

    public ReActStreamingStepProcessor(ReActModelCaller modelCaller,
            ReActResponseParser responseParser,
            ReActStepHandler stepHandler,
            ReActStreamEventWriter streamEventWriter,
            SelfReflector selfReflector) {
        this.modelCaller = modelCaller;
        this.responseParser = responseParser;
        this.stepHandler = stepHandler;
        this.streamEventWriter = streamEventWriter;
        this.selfReflector = selfReflector;
    }

    /**
     * 处理一轮流式模型响应，并在需要时发送工具或总结事件。
     *
     * @param llmResponse 原始模型响应
     * @param messages 可变的 ReAct 消息历史
     * @param toolSpecs 可用的工具描述
     * @param modelId 选中的模型 ID
     * @param finalAnswer 最终回答累加器
     * @param eventEmitter JSON 事件消费者
     * @param recoveryTracker 本次运行的恢复跟踪器
     * @return 循环控制结果
     */
    public ReActLoopStepResult process(AiMessage aiMessage,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecs,
            String modelId,
            StringBuilder finalAnswer,
            Consumer<String> eventEmitter,
            ReActRecoveryTracker recoveryTracker) {
        return process(aiMessage, messages, toolSpecs, modelId, finalAnswer, null, eventEmitter, recoveryTracker, 0);
    }

    /**
     * 处理一轮流式模型响应，并在需要时发送工具或总结事件。
     *
     * @param llmResponse 原始模型响应
     * @param messages 可变的 ReAct 消息历史
     * @param toolSpecs 可用的工具描述
     * @param modelId 选中的模型 ID
     * @param finalAnswer 最终回答累加器
     * @param thinkingSteps 可见思考步骤
     * @param eventEmitter JSON 事件消费者
     * @param recoveryTracker 本次运行的恢复跟踪器
     * @return 循环控制结果
     */
    public ReActLoopStepResult process(AiMessage aiMessage,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecs,
            String modelId,
            StringBuilder finalAnswer,
            List<AnalysisResponse.ThinkingStep> thinkingSteps,
            Consumer<String> eventEmitter,
            ReActRecoveryTracker recoveryTracker) {
        return process(aiMessage, messages, toolSpecs, modelId, finalAnswer, thinkingSteps, eventEmitter,
                recoveryTracker, 0);
    }

    /**
     * 处理一轮流式模型响应，并在需要时发送工具或总结事件。
     *
     * @param llmResponse 原始模型响应
     * @param messages 可变的 ReAct 消息历史
     * @param toolSpecs 可用的工具描述
     * @param modelId 选中的模型 ID
     * @param finalAnswer 最终回答累加器
     * @param thinkingSteps 可见思考步骤
     * @param eventEmitter JSON 事件消费者
     * @param recoveryTracker 本次运行的恢复跟踪器
     * @param iteration 当前迭代次数
     * @return 循环控制结果
     */
    public ReActLoopStepResult process(AiMessage aiMessage,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecs,
            String modelId,
            StringBuilder finalAnswer,
            List<AnalysisResponse.ThinkingStep> thinkingSteps,
            Consumer<String> eventEmitter,
            ReActRecoveryTracker recoveryTracker,
            int iteration) {
        addThinkingStep(aiMessage.text() == null ? "" : aiMessage.text(), thinkingSteps, iteration);
        ReActStepOutcome outcome = stepHandler.handleNative(aiMessage, messages);
        if (outcome.isFinalAnswer()) {
            addAnswerStep(thinkingSteps, iteration, "生成最终回答");
            finalAnswer.append(outcome.answer());
            addFinalReflection(thinkingSteps, iteration, finalAnswer.toString(), eventEmitter);
            return new ReActLoopStepResult(false, false);
        }
        if (outcome.isMemoryIndexed()) {
            return new ReActLoopStepResult(true, true);
        }

        addToolStep(thinkingSteps, outcome, iteration);
        streamEventWriter.emitToolCall(eventEmitter, outcome.toolName(),
                preview(outcome.toolResult(), TOOL_RESULT_PREVIEW_LENGTH));
        ReActRecoveryDecision recoveryDecision = emitReflectionIfNeeded(outcome, eventEmitter, recoveryTracker);
        if (recoveryDecision.recoveryRequired() && !recoveryDecision.allowed()) {
            appendStreamingSummary(messages, modelId, finalAnswer, eventEmitter);
            addAnswerStep(thinkingSteps, iteration, "错误恢复达到上限，生成降级回答");
            addFinalReflection(thinkingSteps, iteration, finalAnswer.toString(), eventEmitter);
            return new ReActLoopStepResult(false, false, false);
        }
        if (outcome.finalAnswerRequired()) {
            appendStreamingSummary(messages, modelId, finalAnswer, eventEmitter);
            addAnswerStep(thinkingSteps, iteration, "整合结果生成最终回答");
            addFinalReflection(thinkingSteps, iteration, finalAnswer.toString(), eventEmitter);
            return new ReActLoopStepResult(false, false);
        }
        return new ReActLoopStepResult(true, false, recoveryDecision.recoveryRequired());
    }

    private void addThinkingStep(String llmResponse, List<AnalysisResponse.ThinkingStep> thinkingSteps, int iteration) {
        if (thinkingSteps == null) {
            return;
        }
        String thinking = responseParser.extractThinking(llmResponse);
        if (thinking != null) {
            thinkingSteps.add(new AnalysisResponse.ThinkingStep(iteration, "thinking", thinking));
        }
    }

    private void addToolStep(List<AnalysisResponse.ThinkingStep> thinkingSteps, ReActStepOutcome outcome,
            int iteration) {
        if (thinkingSteps == null) {
            return;
        }
        AnalysisResponse.ThinkingStep toolStep = new AnalysisResponse.ThinkingStep(iteration, "tool_call", "调用工具");
        toolStep.setToolName(outcome.toolName());
        toolStep.setToolResult(preview(outcome.toolResult(), TOOL_RESULT_PREVIEW_LENGTH));
        thinkingSteps.add(toolStep);
    }

    private void addAnswerStep(List<AnalysisResponse.ThinkingStep> thinkingSteps, int iteration, String content) {
        if (thinkingSteps == null) {
            return;
        }
        thinkingSteps.add(new AnalysisResponse.ThinkingStep(iteration, "answer", content));
    }

    private void addFinalReflection(List<AnalysisResponse.ThinkingStep> thinkingSteps, int iteration, String answer,
            Consumer<String> eventEmitter) {
        if (thinkingSteps == null) {
            emitReflection(eventEmitter, selfReflector.reflectFinalAnswer(answer, List.of()));
            return;
        }
        String reflection = selfReflector.reflectFinalAnswer(answer, thinkingSteps);
        if (!reflection.isBlank()) {
            thinkingSteps.add(new AnalysisResponse.ThinkingStep(iteration, "reflection", reflection));
            emitReflection(eventEmitter, reflection);
        }
    }

    private ReActRecoveryDecision emitReflectionIfNeeded(ReActStepOutcome outcome, Consumer<String> eventEmitter,
            ReActRecoveryTracker recoveryTracker) {
        ReActRecoveryDecision decision = recoveryTracker.recordAttempt(outcome.recoveryAdvice());
        if (!decision.recoveryRequired()) {
            return decision;
        }
        String reflection = decision.allowed()
                ? selfReflector.reflect(outcome, decision.attempt())
                : selfReflector.reflectExhausted(outcome);
        emitReflection(eventEmitter, reflection);
        return decision;
    }

    private void emitReflection(Consumer<String> eventEmitter, String reflection) {
        if (!reflection.isBlank()) {
            streamEventWriter.emitReflection(eventEmitter, reflection);
        }
    }

    private void appendStreamingSummary(List<ChatMessage> messages, String modelId, StringBuilder finalAnswer,
            Consumer<String> eventEmitter) {
        messages.add(AiMessage.from(SUMMARY_INTRODUCTION));
        StringBuilder summaryBuilder = new StringBuilder();
        modelCaller.callStreamingSummary(messages, modelId, token -> {
            summaryBuilder.append(token);
            streamEventWriter.emitToken(eventEmitter, token);
        });
        finalAnswer.append(responseParser.cleanAssistantAnswer(summaryBuilder.toString()));
    }

    private String preview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }
}
