package com.ai.service;

import com.ai.agent.DataAnalysisAgent;
import com.ai.agent.react.ReActStreamEventWriter;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.core.task.AsyncTaskExecutor;

/**
 * 协调分析执行与 SSE 事件投递。
 *
 * @author data-agent
 */
@Service
public class AnalysisStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisStreamService.class);
    private static final String EVENT_START = "start";
    private static final String EVENT_STREAM = "stream";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_SESSION_ID = "sessionId";
    private static final String START_MESSAGE = "开始分析...";
    private static final String ERROR_MESSAGE_PREFIX = "分析过程出错: ";
    private static final String EMPTY_VALUE = "";
    private static final String THINKING_TYPE_REFLECTION = "reflection";
    private static final String THINKING_TYPE_PLAN = "plan";
    private static final String THINKING_TYPE_PARALLEL_PRECHECK = "parallel_precheck";
    private static final String THINKING_TYPE_ORCHESTRATOR = "orchestrator";
    private static final String THINKING_TYPE_ORCHESTRATOR_TASK = "orchestrator_task";
    private static final String DEFAULT_THINKING_TOOL_NAME = "thinking";
    private static final String TITLE_ORCHESTRATOR = "编排决策";

    private final DataAnalysisAgent dataAnalysisAgent;
    private final ReActStreamEventWriter streamEventWriter;
    private final ObjectMapper objectMapper;
    private final AsyncTaskExecutor sseExecutor;
    private final long sseTimeoutMs;

    public AnalysisStreamService(DataAnalysisAgent dataAnalysisAgent,
            ReActStreamEventWriter streamEventWriter,
            ObjectMapper objectMapper,
            @Qualifier("sseExecutor") AsyncTaskExecutor sseExecutor,
            @Value("${app.sse.timeout-ms:600000}") long sseTimeoutMs) {
        this.dataAnalysisAgent = dataAnalysisAgent;
        this.streamEventWriter = streamEventWriter;
        this.objectMapper = objectMapper;
        this.sseExecutor = sseExecutor;
        this.sseTimeoutMs = sseTimeoutMs;
    }

    /**
     * 在 SSE 执行器中启动分析任务。
     *
     * @param request 分析请求
     * @return SSE 发射器
     */
    public SseEmitter stream(AnalysisRequest request) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        AtomicReference<Future<?>> taskRef = new AtomicReference<>();

        // 超时/客户端断开时中断后台分析线程，避免白跑模型调用
        Runnable cancelTask = () -> {
            Future<?> task = taskRef.get();
            if (task != null) {
                task.cancel(true);
            }
        };

        emitter.onCompletion(() -> LOGGER.debug("SSE 流已完成"));
        emitter.onTimeout(() -> {
            LOGGER.warn("SSE 流已超时");
            cancelTask.run();
        });
        emitter.onError(ex -> {
            LOGGER.warn("SSE 流异常断开: {}", ex.getMessage());
            cancelTask.run();
        });

        Future<?> future = sseExecutor.submit(() -> executeStreamingAnalysis(request, emitter));
        taskRef.set(future);
        return emitter;
    }

    private void executeStreamingAnalysis(AnalysisRequest request, SseEmitter emitter) {
        try {
            sendEvent(emitter, EVENT_START, Map.of(
                    KEY_MESSAGE, START_MESSAGE,
                    KEY_SESSION_ID, resolveSessionId(request, null)));

            // 真流式：事件实时推送，不再阻塞等全部完成
            dataAnalysisAgent.analyzeStreaming(request, eventJson -> sendRawEvent(emitter, eventJson));
            emitter.complete();
        } catch (Exception e) {
            LOGGER.error("SSE 流式分析失败", e);
            sendErrorEvent(emitter, e);
            completeQuietly(emitter);
        }
    }

    private void emitAnalysisResponse(AnalysisRequest request, AnalysisResponse response, SseEmitter emitter) {
        if (response == null) {
            streamEventWriter.emitError(eventJson -> sendRawEvent(emitter, eventJson), "分析结果为空");
            streamEventWriter.emitDone(eventJson -> sendRawEvent(emitter, eventJson), resolveSessionId(request, null));
            return;
        }
        LOGGER.info("SSE emitting thinkingSteps count={}", response.getThinkingSteps() == null ? 0 : response.getThinkingSteps().size());
        emitThinkingSteps(response.getThinkingSteps(), emitter);
        if (response.isSuccess()) {
            streamEventWriter.emitToken(eventJson -> sendRawEvent(emitter, eventJson), resolveResultText(response));
        } else {
            streamEventWriter.emitError(eventJson -> sendRawEvent(emitter, eventJson), resolveErrorText(response));
        }
        streamEventWriter.emitDone(eventJson -> sendRawEvent(emitter, eventJson), resolveSessionId(request, response),
                response.getTraceId());
    }

    private void emitThinkingSteps(List<AnalysisResponse.ThinkingStep> thinkingSteps, SseEmitter emitter) {
        if (thinkingSteps == null || thinkingSteps.isEmpty()) {
            return;
        }
        for (AnalysisResponse.ThinkingStep step : thinkingSteps) {
            String content = resolveThinkingContent(step);
            if (!StringUtils.hasText(content)) {
                continue;
            }
            if (THINKING_TYPE_REFLECTION.equals(step.getType())) { 
                streamEventWriter.emitReflection(eventJson -> sendRawEvent(emitter, eventJson), content);
                continue;
            }
            if (THINKING_TYPE_PLAN.equals(step.getType())) {
                streamEventWriter.emitExecutionPlan(eventJson -> sendRawEvent(emitter, eventJson), content);
                continue;
            }
            if (THINKING_TYPE_PARALLEL_PRECHECK.equals(step.getType())) {
                streamEventWriter.emitParallelPrecheck(eventJson -> sendRawEvent(emitter, eventJson), content);
                continue;
            }
            if (isOrchestratorStep(step)) {
                streamEventWriter.emitOrchestration(eventJson -> sendRawEvent(emitter, eventJson),
                        resolveOrchestrationTitle(step), content);
                continue;
            }
            streamEventWriter.emitToolCall(eventJson -> sendRawEvent(emitter, eventJson),
                    resolveThinkingName(step), content);
        }
    }

    private boolean isOrchestratorStep(AnalysisResponse.ThinkingStep step) {
        return THINKING_TYPE_ORCHESTRATOR.equals(step.getType())
                || THINKING_TYPE_ORCHESTRATOR_TASK.equals(step.getType());
    }

    private String resolveOrchestrationTitle(AnalysisResponse.ThinkingStep step) {
        if (THINKING_TYPE_ORCHESTRATOR.equals(step.getType())) {
            return TITLE_ORCHESTRATOR;
        }
        return "编排任务 " + step.getStep();
    }

    private String resolveThinkingName(AnalysisResponse.ThinkingStep step) {
        if (StringUtils.hasText(step.getToolName())) {
            return step.getToolName();
        }
        if (StringUtils.hasText(step.getType())) {
            return step.getType();
        }
        return DEFAULT_THINKING_TOOL_NAME;
    }

    private String resolveThinkingContent(AnalysisResponse.ThinkingStep step) {
        if (StringUtils.hasText(step.getToolResult())) {
            return step.getToolResult();
        }
        return step.getContent();
    }

    private String resolveResultText(AnalysisResponse response) {
        return StringUtils.hasText(response.getResult()) ? response.getResult() : EMPTY_VALUE;
    }

    private String resolveErrorText(AnalysisResponse response) {
        if (StringUtils.hasText(response.getError())) {
            return response.getError();
        }
        return "分析失败";
    }

    private String resolveSessionId(AnalysisRequest request, AnalysisResponse response) {
        if (response != null && StringUtils.hasText(response.getSessionId())) {
            return response.getSessionId();
        }
        return request != null && StringUtils.hasText(request.getSessionId()) ? request.getSessionId() : EMPTY_VALUE;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Map<String, Object> payload) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(objectMapper.writeValueAsString(payload)));
    }

    private void sendRawEvent(SseEmitter emitter, String eventJson) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(EVENT_STREAM).data(eventJson));
        } catch (Exception e) {
            LOGGER.warn("发送 SSE 事件失败", e);
        }
    }

    private void sendErrorEvent(SseEmitter emitter, Exception exception) {
        streamEventWriter.emitError(eventJson -> sendRawEvent(emitter, eventJson),
                ERROR_MESSAGE_PREFIX + exception.getMessage());
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception completeException) {
            LOGGER.debug("SSE 流关闭时已不可写: {}", completeException.getMessage());
        }
    }
}
