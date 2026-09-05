package com.ai.agent.react;

import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.AgentRuntimeProperties;
import com.ai.logging.StructuredLogger;
import com.ai.model.AnalysisResponse;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import com.ai.agent.tool.governance.AgentToolInvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 执行同步和流式 ReAct 循环。
 *
 * <p>该执行器只负责模型循环机制，请求准备、会话持久化、RAG 检索和 HTTP/SSE 传输
 * 均由外层组件负责。</p>
 *
 * @author data-agent
 */
@Component
public class ReActLoopRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReActLoopRunner.class);
    private static final int LOG_QUERY_PREVIEW_LENGTH = 50;
    // [A] intent-without-action 守卫：纠偏重试上限与提示语、意图文本最大长度
    private static final int MAX_TOOL_INTENT_NUDGES = 2;
    private static final int TOOL_INTENT_MAX_LENGTH = 80;
    private static final String TOOL_INTENT_NUDGE =
            "请立即直接发起工具调用来获取数据，不要只用文字描述你的计划或意图。";
    private static final String MODEL_FAILURE_MESSAGE = "模型调用失败，请稍后重试";
    private static final String MAX_ITERATION_ANSWER_PREFIX = "分析步骤较多，以下是目前已获得的分析结果:\n";
    private static final String MAX_ITERATION_RETRY_PREFIX = "达到最大迭代次数后，请基于现有信息给出保守结论。";
    private static final String STEP_START = "REACT_START";
    private static final String STEP_ITERATION_START = "REACT_ITERATION_START";
    private static final String STEP_ITERATION_RESULT = "REACT_ITERATION_RESULT";
    private static final String STEP_EMPTY_RESPONSE = "REACT_EMPTY_RESPONSE";
    private static final String STEP_MAX_ITERATIONS = "REACT_MAX_ITERATIONS";
    private static final String STEP_COMPLETION = "REACT_COMPLETION";
    private static final String KEY_STREAMING = "streaming";
    private static final String KEY_MODEL_ID = "modelId";
    private static final String KEY_QUERY_LENGTH = "queryLength";
    private static final String KEY_TOOL_COUNT = "toolCount";
    private static final String KEY_ITERATION = "iteration";
    private static final String KEY_MAX_ITERATIONS = "maxIterations";
    private static final String KEY_LLM_RESPONSE_LENGTH = "llmResponseLength";
    private static final String KEY_SHOULD_CONTINUE = "shouldContinue";
    private static final String KEY_MEMORY_INDEXED = "memoryIndexed";
    private static final String KEY_RECOVERY_REQUIRED = "recoveryRequired";
    private static final String KEY_FINAL_ANSWER_LENGTH = "finalAnswerLength";
    private static final String KEY_THINKING_STEP_COUNT = "thinkingStepCount";
    private static final String KEY_ANSWER_LENGTH = "answerLength";

    private final ReActModelCaller modelCaller;
    private final ReActResponseParser responseParser;
    private final ReActSynchronousStepProcessor synchronousStepProcessor;
    private final ReActStreamingStepProcessor streamingStepProcessor;
    private final ReActStreamEventWriter streamEventWriter;
    private final ReActWorkingMemoryService workingMemoryService;
    private final StructuredLogger structuredLogger;
    private final AgentRuntimeProperties runtimeProperties;

    public ReActLoopRunner(ReActModelCaller modelCaller,
            ReActResponseParser responseParser,
            ReActSynchronousStepProcessor synchronousStepProcessor,
            ReActStreamingStepProcessor streamingStepProcessor,
            ReActStreamEventWriter streamEventWriter,
            ReActWorkingMemoryService workingMemoryService,
            StructuredLogger structuredLogger,
            AgentRuntimeProperties runtimeProperties) {
        this.modelCaller = modelCaller;
        this.responseParser = responseParser;
        this.synchronousStepProcessor = synchronousStepProcessor;
        this.streamingStepProcessor = streamingStepProcessor;
        this.streamEventWriter = streamEventWriter;
        this.workingMemoryService = workingMemoryService;
        this.structuredLogger = structuredLogger;
        this.runtimeProperties = runtimeProperties;
    }

    /**
     * 执行一次同步 ReAct 循环。
     *
     * @param messages 可变消息历史
     * @param toolSpecs 可用工具规格
     * @param modelId 选中的模型编号
     * @param sessionId 当前会话编号
     * @param userQuery 用于日志和工作记忆的用户问题
     * @param thinkingSteps 可见推理步骤
     * @return 循环执行结果
     */
    public ReActExecutionResult run(List<ChatMessage> messages,
            List<ToolSpecification> toolSpecs,
            String modelId,
            String sessionId,
            String userQuery,
            List<AnalysisResponse.ThinkingStep> thinkingSteps,
            AgentToolInvocationContext invocationContext) {
        StringBuilder finalAnswer = new StringBuilder();
        int iterations = currentIterations();
        int maxIterations = resolveMaxIterations();
        int toolCallCount = 0;
        boolean success = true;
        boolean continuationRequested = false;
        boolean suspended = false;
        String approvalId = null;
        ReActRecoveryTracker recoveryTracker = new ReActRecoveryTracker();
        workingMemoryService.recordStart(sessionId, userQuery);
        logStart(sessionId, userQuery, modelId, toolSpecs, false);
        while (iterations < maxIterations) {
            beforeIteration();
            iterations++;
            LOGGER.info("ReAct iteration {}/{} for query: {}", iterations, maxIterations,
                    preview(userQuery, LOG_QUERY_PREVIEW_LENGTH));
            logIterationStart(sessionId, modelId, toolSpecs, iterations, maxIterations, false);

            ChatResponse response = modelCaller.callWithTools(messages, toolSpecs, modelId);
            AiMessage aiMessage = response == null ? null : response.aiMessage();
            if (isEmptyResponse(aiMessage)) {
                finalAnswer.append(MODEL_FAILURE_MESSAGE);
                thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "error", "模型调用失败"));
                workingMemoryService.recordFailure(sessionId, userQuery, iterations);
                logEmptyResponse(sessionId, modelId, iterations, false);
                success = false;
                break;
            }

            if (aiMessage.hasToolExecutionRequests()) {
                toolCallCount += aiMessage.toolExecutionRequests().size();
            }

            String llmResponse = describeAiMessage(aiMessage);
            ReActLoopStepResult stepResult = synchronousStepProcessor.process(aiMessage, toolSpecs, modelId, messages,
                    finalAnswer, thinkingSteps, iterations, recoveryTracker, invocationContext);
            workingMemoryService.recordIteration(sessionId, userQuery, iterations, llmResponse, stepResult,
                    finalAnswer.toString());
            logIterationResult(sessionId, modelId, llmResponse, finalAnswer, thinkingSteps, iterations, stepResult,
                    false);
            continuationRequested = stepResult.shouldContinue();
            if (stepResult.suspended()) {
                suspended = true;
                approvalId = stepResult.approvalId();
                break;
            }
            if (!continuationRequested) {
                break;
            }
        }
        if (!suspended) {
            enforceIterationLimit(continuationRequested, iterations, maxIterations);
            appendMaxIterationAnswer(messages, finalAnswer, thinkingSteps, iterations, maxIterations);
        }
        String answer = finalAnswer.toString().trim();
        if (continuationRequested && iterations >= maxIterations) {
            thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "reflection",
                    "已达到最大迭代次数，建议压缩问题范围或补充更多上下文后再试"));
            logMaxIterations(sessionId, modelId, iterations, false);
        }
        if (!suspended) {
            workingMemoryService.recordCompletion(sessionId, userQuery, iterations, answer);
            logCompletion(sessionId, modelId, iterations, answer, thinkingSteps, false);
        }
        return new ReActExecutionResult(answer, iterations, toolCallCount, success, suspended, approvalId);
    }

    /**
     * 执行一次流式 ReAct 循环并返回最终累积答案与迭代次数。
     *
     * <p>返回 {@link ReActExecutionResult}（与同步 {@link #run} 对齐），便于上层记录
     * 执行轨迹——流式路径不再丢失「迭代轮数 / 工具调用次数」这两个可观测性指标。</p>
     *
     * @param messages 可变消息历史
     * @param toolSpecs 可用工具规格
     * @param modelId 选中的模型编号
     * @param sessionId 当前会话编号
     * @param userQuery 用于工作记忆的用户问题
     * @param eventEmitter JSON 事件消费者
     * @return 循环执行结果（最终答案 + 实际轮数）
     */
    public ReActExecutionResult runStreaming(List<ChatMessage> messages,
            List<ToolSpecification> toolSpecs,
            String modelId,
            String sessionId,
            String userQuery,
            Consumer<String> eventEmitter,
            AgentToolInvocationContext invocationContext) {
        StringBuilder finalAnswer = new StringBuilder();
        List<AnalysisResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
        int iterations = currentIterations();
        int maxIterations = resolveMaxIterations();
        int toolIntentNudges = 0;
        int toolCallCount = 0;
        boolean success = true;
        boolean continuationRequested = false;
        boolean suspended = false;
        String approvalId = null;
        ReActRecoveryTracker recoveryTracker = new ReActRecoveryTracker();
        workingMemoryService.recordStart(sessionId, userQuery);
        logStart(sessionId, userQuery, modelId, toolSpecs, true);
        while (iterations < maxIterations) {
            beforeIteration();
            iterations++;
            LOGGER.info("ReAct streaming iteration {}/{}", iterations, maxIterations);
            logIterationStart(sessionId, modelId, toolSpecs, iterations, maxIterations, true);
            streamEventWriter.emitThinkingStart(eventEmitter, iterations);
            // [B] 工具决策轮改用非流式调用：返回完整的 {content, tool_calls} 对象，跨模型一致，
            // 规避流式增量拼接导致部分模型（如先吐旁白的）丢失 tool_call 的失败模式
            ChatResponse response = modelCaller.callWithTools(messages, toolSpecs, modelId);
            AiMessage aiMessage = response == null ? null : response.aiMessage();
            if (isEmptyResponse(aiMessage)) {
                streamEventWriter.emitError(eventEmitter, MODEL_FAILURE_MESSAGE);
                thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "error", "模型调用失败"));
                workingMemoryService.recordFailure(sessionId, userQuery, iterations);
                logEmptyResponse(sessionId, modelId, iterations, true);
                success = false;
                break;
            }

            String llmResponse = describeAiMessage(aiMessage);
            // [Day3 学习] 打印每轮模型原始输出，观察它如何思考、决定调哪个工具或给出最终回答
            LOGGER.info("【ReAct第{}轮·模型原文】\n{}", iterations, llmResponse);
            // [Day13 可观测性] 累计本轮发起的工具调用次数，用于执行轨迹统计
            if (aiMessage.hasToolExecutionRequests()) {
                toolCallCount += aiMessage.toolExecutionRequests().size();
            }
            // [A] intent-without-action 守卫：模型只用文字说要调工具却没真发起 tool_call 时，
            // 注入纠偏提示重试一轮。纯启发式、不依赖任何厂商，是模型无关的安全网
            if (!aiMessage.hasToolExecutionRequests()
                    && toolIntentNudges < MAX_TOOL_INTENT_NUDGES
                    && looksLikeUnfulfilledToolIntent(aiMessage.text())) {
                toolIntentNudges++;
                messages.add(aiMessage);
                messages.add(UserMessage.from(TOOL_INTENT_NUDGE));
                LOGGER.info("检测到'光说不练'，注入纠偏提示重试(第{}次): {}", toolIntentNudges,
                        preview(aiMessage.text(), LOG_QUERY_PREVIEW_LENGTH));
                continuationRequested = true;
                continue;
            }
            ReActLoopStepResult stepResult = streamingStepProcessor.process(aiMessage, messages, toolSpecs,
                    modelId, finalAnswer, thinkingSteps, eventEmitter, recoveryTracker, iterations,
                    invocationContext);
            workingMemoryService.recordIteration(sessionId, userQuery, iterations, llmResponse, stepResult,
                    finalAnswer.toString());
            logIterationResult(sessionId, modelId, llmResponse, finalAnswer, thinkingSteps, iterations, stepResult,
                    true);
            continuationRequested = stepResult.shouldContinue();
            if (stepResult.suspended()) {
                suspended = true;
                approvalId = stepResult.approvalId();
                break;
            }
            if (!continuationRequested) {
                break;
            }
        }
        if (!suspended) {
            enforceIterationLimit(continuationRequested, iterations, maxIterations);
        }
        String answer = finalAnswer.toString().trim();
        if (continuationRequested && iterations >= maxIterations) {
            streamEventWriter.emitReflection(eventEmitter,
                    MAX_ITERATION_RETRY_PREFIX + " 已达到最大迭代次数，建议缩小问题范围。");
            thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "max_iterations",
                    "达到最大迭代次数，建议缩小问题范围或补充更多上下文后再试"));
            logMaxIterations(sessionId, modelId, iterations, true);
        }
        if (!suspended) {
            workingMemoryService.recordCompletion(sessionId, userQuery, iterations, answer);
            logCompletion(sessionId, modelId, iterations, answer, thinkingSteps, true);
        }
        return new ReActExecutionResult(answer, iterations, toolCallCount, success, suspended, approvalId);
    }

    private void logStart(String sessionId, String userQuery, String modelId, List<ToolSpecification> toolSpecs,
            boolean streaming) {
        Map<String, Object> data = baseStepPayload(modelId, streaming);
        data.put(KEY_QUERY_LENGTH, lengthOf(userQuery));
        data.put(KEY_TOOL_COUNT, toolSpecs == null ? 0 : toolSpecs.size());
        structuredLogger.logAgentStep(sessionId, STEP_START, data);
    }

    private void logIterationStart(String sessionId, String modelId, List<ToolSpecification> toolSpecs,
            int iteration, int maxIterations, boolean streaming) {
        Map<String, Object> data = baseStepPayload(modelId, streaming);
        data.put(KEY_ITERATION, iteration);
        data.put(KEY_MAX_ITERATIONS, maxIterations);
        data.put(KEY_TOOL_COUNT, toolSpecs == null ? 0 : toolSpecs.size());
        structuredLogger.logAgentStep(sessionId, STEP_ITERATION_START, data);
    }

    private void logIterationResult(String sessionId, String modelId, String llmResponse,
            StringBuilder finalAnswer, List<AnalysisResponse.ThinkingStep> thinkingSteps, int iteration,
            ReActLoopStepResult stepResult, boolean streaming) {
        Map<String, Object> data = baseStepPayload(modelId, streaming);
        data.put(KEY_ITERATION, iteration);
        data.put(KEY_LLM_RESPONSE_LENGTH, lengthOf(llmResponse));
        data.put(KEY_SHOULD_CONTINUE, stepResult.shouldContinue());
        data.put(KEY_MEMORY_INDEXED, stepResult.memoryIndexed());
        data.put(KEY_RECOVERY_REQUIRED, stepResult.recoveryRequired());
        data.put(KEY_FINAL_ANSWER_LENGTH, finalAnswer.length());
        data.put(KEY_THINKING_STEP_COUNT, thinkingSteps == null ? 0 : thinkingSteps.size());
        structuredLogger.logAgentStep(sessionId, STEP_ITERATION_RESULT, data);
    }

    private void logEmptyResponse(String sessionId, String modelId, int iteration, boolean streaming) {
        Map<String, Object> data = baseStepPayload(modelId, streaming);
        data.put(KEY_ITERATION, iteration);
        structuredLogger.logAgentStep(sessionId, STEP_EMPTY_RESPONSE, data);
    }

    private void logMaxIterations(String sessionId, String modelId, int iterations, boolean streaming) {
        Map<String, Object> data = baseStepPayload(modelId, streaming);
        data.put(KEY_ITERATION, iterations);
        data.put(KEY_MAX_ITERATIONS, resolveMaxIterations());
        structuredLogger.logAgentStep(sessionId, STEP_MAX_ITERATIONS, data);
    }

    private void logCompletion(String sessionId, String modelId, int iterations, String answer,
            List<AnalysisResponse.ThinkingStep> thinkingSteps, boolean streaming) {
        Map<String, Object> data = baseStepPayload(modelId, streaming);
        data.put(KEY_ITERATION, iterations);
        data.put(KEY_ANSWER_LENGTH, lengthOf(answer));
        data.put(KEY_THINKING_STEP_COUNT, thinkingSteps == null ? 0 : thinkingSteps.size());
        structuredLogger.logAgentStep(sessionId, STEP_COMPLETION, data);
    }

    private Map<String, Object> baseStepPayload(String modelId, boolean streaming) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_MODEL_ID, modelId);
        data.put(KEY_STREAMING, streaming);
        return data;
    }

    private void appendMaxIterationAnswer(List<ChatMessage> messages, StringBuilder finalAnswer,
            List<AnalysisResponse.ThinkingStep> thinkingSteps, int iterations, int maxIterations) {
        if (iterations < maxIterations || !finalAnswer.isEmpty()) {
            return;
        }
        finalAnswer.append(MAX_ITERATION_ANSWER_PREFIX);
        for (ChatMessage message : messages) {
            if (message instanceof AiMessage) {
                appendCleanAiMessage(finalAnswer, (AiMessage) message);
            }
        }
        thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "max_iterations", "达到最大迭代次数，整合已有结果"));
    }

    private int resolveMaxIterations() {
        return AgentRunScope.current()
                .map(AgentRunContext::control)
                .map(control -> control.getLimits().maxIterations())
                .orElse(runtimeProperties.getMaxIterations());
    }

    private int currentIterations() {
        return AgentRunScope.current()
                .map(AgentRunContext::snapshot)
                .map(snapshot -> snapshot.iterations())
                .orElse(0);
    }

    private void beforeIteration() {
        AgentRunScope.current().ifPresent(context -> context.control().beforeIteration());
    }

    private void enforceIterationLimit(boolean continuationRequested, int iterations, int maxIterations) {
        if (!continuationRequested || iterations < maxIterations) {
            return;
        }
        // 再申请一次只用于稳定发布 ITERATION_LIMIT，不会启动额外模型调用。
        AgentRunScope.current().ifPresent(context -> context.control().beforeIteration());
    }

    private void appendCleanAiMessage(StringBuilder finalAnswer, AiMessage message) {
        String text = message.text();
        if (text == null || text.isEmpty()) {
            return;
        }
        String clean = responseParser.cleanAssistantAnswer(text);
        if (!clean.isEmpty()) {
            finalAnswer.append(clean).append("\n");
        }
    }

    /**
     * [A] 判断模型是否"光说不练"：本轮没有发起任何工具调用，但文字内容是一段
     * 简短的"我打算调用工具"的意图描述（而非真正的最终答案）。
     *
     * <p>纯启发式、不依赖任何厂商：真正的最终答案通常较长且不含调用意图措辞，
     * 而失败时模型常输出"我先查询/让我调用/直接调用工具获取数据"这类短意图句却不发起调用。</p>
     *
     * @param text 模型本轮文本
     * @return 是否疑似未兑现的工具意图
     */
    private boolean looksLikeUnfulfilledToolIntent(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.strip();
        // 较长文本更可能是真正的最终答案，不拦截
        if (trimmed.isEmpty() || trimmed.length() > TOOL_INTENT_MAX_LENGTH) {
            return false;
        }
        return trimmed.contains("我先") || trimmed.contains("让我") || trimmed.contains("我将")
                || trimmed.contains("我直接") || trimmed.contains("调用") || trimmed.contains("查询工具")
                || trimmed.contains("获取数据") || trimmed.contains("搜索一下") || trimmed.contains("先获取");
    }

    /**
     * 判断模型响应是否为空：既无文本也无原生工具调用请求时视为调用失败。
     */
    private boolean isEmptyResponse(AiMessage aiMessage) {
        if (aiMessage == null) {
            return true;
        }
        boolean noText = aiMessage.text() == null || aiMessage.text().isEmpty();
        return noText && !aiMessage.hasToolExecutionRequests();
    }

    /**
     * 将 AI 消息转为可记录文本；工具参数不进入日志或工作记忆。
     */
    private String describeAiMessage(AiMessage aiMessage) {
        if (aiMessage.text() != null && !aiMessage.text().isEmpty()) {
            return aiMessage.text();
        }
        StringBuilder description = new StringBuilder();
        for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
            description.append("[tool_call] ").append(request.name())
                    .append("(argumentsLength=")
                    .append(lengthOf(request.arguments()))
                    .append(")\n");
        }
        return description.toString();
    }

    private String preview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }
}
