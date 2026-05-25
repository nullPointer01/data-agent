package com.ai.agent.react;

import com.ai.logging.StructuredLogger;
import com.ai.model.AnalysisResponse;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
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
    private static final int MAX_ITERATIONS = 8;
    private static final int LOG_QUERY_PREVIEW_LENGTH = 50;
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

    public ReActLoopRunner(ReActModelCaller modelCaller,
            ReActResponseParser responseParser,
            ReActSynchronousStepProcessor synchronousStepProcessor,
            ReActStreamingStepProcessor streamingStepProcessor,
            ReActStreamEventWriter streamEventWriter,
            ReActWorkingMemoryService workingMemoryService,
            StructuredLogger structuredLogger) {
        this.modelCaller = modelCaller;
        this.responseParser = responseParser;
        this.synchronousStepProcessor = synchronousStepProcessor;
        this.streamingStepProcessor = streamingStepProcessor;
        this.streamEventWriter = streamEventWriter;
        this.workingMemoryService = workingMemoryService;
        this.structuredLogger = structuredLogger;
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
            List<AnalysisResponse.ThinkingStep> thinkingSteps) {
        StringBuilder finalAnswer = new StringBuilder();
        int iterations = 0;
        ReActRecoveryTracker recoveryTracker = new ReActRecoveryTracker();
        workingMemoryService.recordStart(sessionId, userQuery);
        logStart(sessionId, userQuery, modelId, toolSpecs, false);
        while (iterations < MAX_ITERATIONS) {
            iterations++;
            LOGGER.info("ReAct iteration {}/{} for query: {}", iterations, MAX_ITERATIONS,
                    preview(userQuery, LOG_QUERY_PREVIEW_LENGTH));
            logIterationStart(sessionId, modelId, toolSpecs, iterations, false);

            String llmResponse = modelCaller.callWithTools(messages, toolSpecs, modelId);
            if (llmResponse == null || llmResponse.isEmpty()) {
                finalAnswer.append(MODEL_FAILURE_MESSAGE);
                thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "error", "模型调用失败"));
                workingMemoryService.recordFailure(sessionId, userQuery, iterations);
                logEmptyResponse(sessionId, modelId, iterations, false);
                break;
            }

            ReActLoopStepResult stepResult = synchronousStepProcessor.process(llmResponse, toolSpecs, modelId, messages,
                    finalAnswer, thinkingSteps, iterations, recoveryTracker);
            workingMemoryService.recordIteration(sessionId, userQuery, iterations, llmResponse, stepResult,
                    finalAnswer.toString());
            logIterationResult(sessionId, modelId, llmResponse, finalAnswer, thinkingSteps, iterations, stepResult,
                    false);
            if (!stepResult.shouldContinue()) {
                break;
            }
            if (stepResult.memoryIndexed()) {
                iterations--;
            }
        }
        appendMaxIterationAnswer(messages, finalAnswer, thinkingSteps, iterations);
        String answer = finalAnswer.toString().trim();
        if (iterations >= MAX_ITERATIONS) {
            thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "reflection",
                    "已达到最大迭代次数，建议压缩问题范围或补充更多上下文后再试"));
            logMaxIterations(sessionId, modelId, iterations, false);
        }
        workingMemoryService.recordCompletion(sessionId, userQuery, iterations, answer);
        logCompletion(sessionId, modelId, iterations, answer, thinkingSteps, false);
        return new ReActExecutionResult(answer, iterations);
    }

    /**
     * 执行一次流式 ReAct 循环并返回最终累积答案。
     *
     * @param messages 可变消息历史
     * @param toolSpecs 可用工具规格
     * @param modelId 选中的模型编号
     * @param sessionId 当前会话编号
     * @param userQuery 用于工作记忆的用户问题
     * @param eventEmitter JSON 事件消费者
     * @return 最终答案
     */
    public String runStreaming(List<ChatMessage> messages,
            List<ToolSpecification> toolSpecs,
            String modelId,
            String sessionId,
            String userQuery,
            Consumer<String> eventEmitter) {
        StringBuilder finalAnswer = new StringBuilder();
        List<AnalysisResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
        int iterations = 0;
        ReActRecoveryTracker recoveryTracker = new ReActRecoveryTracker();
        workingMemoryService.recordStart(sessionId, userQuery);
        logStart(sessionId, userQuery, modelId, toolSpecs, true);
        while (iterations < MAX_ITERATIONS) {
            iterations++;
            LOGGER.info("ReAct streaming iteration {}/{}", iterations, MAX_ITERATIONS);
            logIterationStart(sessionId, modelId, toolSpecs, iterations, true);
            streamEventWriter.emitThinkingStart(eventEmitter, iterations);
            String llmResponse = modelCaller.callStreamingWithTools(messages, toolSpecs, modelId, token -> {
                streamEventWriter.emitToken(eventEmitter, token);
            });
            if (llmResponse == null || llmResponse.isEmpty()) {
                streamEventWriter.emitError(eventEmitter, MODEL_FAILURE_MESSAGE);
                thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "error", "模型调用失败"));
                workingMemoryService.recordFailure(sessionId, userQuery, iterations);
                logEmptyResponse(sessionId, modelId, iterations, true);
                break;
            }

            ReActLoopStepResult stepResult = streamingStepProcessor.process(llmResponse, messages, toolSpecs,
                    modelId, finalAnswer, thinkingSteps, eventEmitter, recoveryTracker, iterations);
            workingMemoryService.recordIteration(sessionId, userQuery, iterations, llmResponse, stepResult,
                    finalAnswer.toString());
            logIterationResult(sessionId, modelId, llmResponse, finalAnswer, thinkingSteps, iterations, stepResult,
                    true);
            if (!stepResult.shouldContinue()) {
                break;
            }
            if (stepResult.memoryIndexed()) {
                iterations--;
            }
        }
        String answer = finalAnswer.toString().trim();
        if (iterations >= MAX_ITERATIONS) {
            streamEventWriter.emitReflection(eventEmitter,
                    MAX_ITERATION_RETRY_PREFIX + " 已达到最大迭代次数，建议缩小问题范围。");
            thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "max_iterations",
                    "达到最大迭代次数，建议缩小问题范围或补充更多上下文后再试"));
            logMaxIterations(sessionId, modelId, iterations, true);
        }
        workingMemoryService.recordCompletion(sessionId, userQuery, iterations, answer);
        logCompletion(sessionId, modelId, iterations, answer, thinkingSteps, true);
        return answer;
    }

    private void logStart(String sessionId, String userQuery, String modelId, List<ToolSpecification> toolSpecs,
            boolean streaming) {
        Map<String, Object> data = baseStepPayload(modelId, streaming);
        data.put(KEY_QUERY_LENGTH, lengthOf(userQuery));
        data.put(KEY_TOOL_COUNT, toolSpecs == null ? 0 : toolSpecs.size());
        structuredLogger.logAgentStep(sessionId, STEP_START, data);
    }

    private void logIterationStart(String sessionId, String modelId, List<ToolSpecification> toolSpecs,
            int iteration, boolean streaming) {
        Map<String, Object> data = baseStepPayload(modelId, streaming);
        data.put(KEY_ITERATION, iteration);
        data.put(KEY_MAX_ITERATIONS, MAX_ITERATIONS);
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
        data.put(KEY_MAX_ITERATIONS, MAX_ITERATIONS);
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
            List<AnalysisResponse.ThinkingStep> thinkingSteps, int iterations) {
        if (iterations < MAX_ITERATIONS || !finalAnswer.isEmpty()) {
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
