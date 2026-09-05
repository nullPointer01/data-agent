package com.ai.service;

import com.ai.agent.DataAnalysisAgent;
import com.ai.agent.react.ReActStreamEventWriter;
import com.ai.agent.runtime.AgentRunRegistry;
import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentEventSink;
import com.ai.agent.runtime.event.AgentEventType;
import com.ai.agent.runtime.event.AgentSseEventWriter;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 协调 Agent Run 与 SSE 连接生命周期。
 *
 * @author data-agent
 */
@Service
public class AnalysisStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisStreamService.class);
    private static final String EVENT_STREAM = "stream";

    private final DataAnalysisAgent dataAnalysisAgent;
    private final AgentSseEventWriter agentSseEventWriter;
    private final ReActStreamEventWriter legacyEventWriter;
    private final AgentRunRegistry runRegistry;
    private final AsyncTaskExecutor sseExecutor;
    private final long sseTimeoutMs;

    public AnalysisStreamService(DataAnalysisAgent dataAnalysisAgent,
            AgentSseEventWriter agentSseEventWriter,
            ReActStreamEventWriter legacyEventWriter,
            AgentRunRegistry runRegistry,
            @Qualifier("sseExecutor") AsyncTaskExecutor sseExecutor,
            @Value("${app.sse.timeout-ms:600000}") long sseTimeoutMs) {
        this.dataAnalysisAgent = dataAnalysisAgent;
        this.agentSseEventWriter = agentSseEventWriter;
        this.legacyEventWriter = legacyEventWriter;
        this.runRegistry = runRegistry;
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
        AtomicReference<String> runIdRef = new AtomicReference<>();
        AtomicBoolean terminalDelivered = new AtomicBoolean();

        Runnable cancelTask = () -> cancelActiveRun(runIdRef.get(), taskRef.get());
        emitter.onCompletion(() -> {
            if (!terminalDelivered.get()) {
                cancelTask.run();
            }
            LOGGER.debug("SSE 流已完成: runId={}", runIdRef.get());
        });
        emitter.onTimeout(() -> {
            LOGGER.warn("SSE 流已超时: runId={}", runIdRef.get());
            cancelTask.run();
        });
        emitter.onError(ex -> {
            LOGGER.warn("SSE 流异常断开: runId={}, error={}", runIdRef.get(), ex.getMessage());
            cancelTask.run();
        });

        Future<?> future = sseExecutor.submit(
                () -> executeStreamingAnalysis(request, emitter, runIdRef, terminalDelivered));
        taskRef.set(future);
        return emitter;
    }

    private void executeStreamingAnalysis(AnalysisRequest request, SseEmitter emitter,
            AtomicReference<String> runIdRef, AtomicBoolean terminalDelivered) {
        AgentEventSink transportSink = agentSseEventWriter.createSink(
                eventJson -> sendRawEvent(emitter, eventJson));
        AgentEventSink trackingSink = trackingSink(transportSink, runIdRef, terminalDelivered);
        try {
            AnalysisResponse response = dataAnalysisAgent.analyzeStreaming(request, trackingSink);
            if (response == null || !StringUtils.hasText(response.getRunId())) {
                emitRejectedRequest(response, request, emitter);
            }
            emitter.complete();
        } catch (Exception e) {
            String runId = runIdRef.get();
            if (StringUtils.hasText(runId)) {
                runRegistry.cancelSystem(runId, "SSE 连接不可用");
            } else {
                emitRejectedRequest(AnalysisResponse.fail(safeMessage(e)), request, emitter);
            }
            completeQuietly(emitter);
        }
    }

    private AgentEventSink trackingSink(AgentEventSink delegate, AtomicReference<String> runIdRef,
            AtomicBoolean terminalDelivered) {
        return new AgentEventSink() {
            @Override
            public void emit(AgentEvent event) {
                if (event.type() == AgentEventType.RUN_STARTED) {
                    runIdRef.compareAndSet(null, event.runId());
                }
                delegate.emit(event);
                if (event.type().isTerminal()) {
                    terminalDelivered.set(true);
                }
            }

            @Override
            public boolean isStreaming() {
                return true;
            }
        };
    }

    private void emitRejectedRequest(AnalysisResponse response, AnalysisRequest request, SseEmitter emitter) {
        String error = response != null && StringUtils.hasText(response.getError())
                ? response.getError()
                : "分析请求未被接受";
        legacyEventWriter.emitError(event -> sendRawEvent(emitter, event), error);
        legacyEventWriter.emitDone(event -> sendRawEvent(emitter, event),
                request == null ? null : request.getSessionId());
    }

    private void cancelActiveRun(String runId, Future<?> task) {
        if (StringUtils.hasText(runId)) {
            runRegistry.cancelSystem(runId, "SSE 客户端断开或超时");
        }
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
    }

    private void sendRawEvent(SseEmitter emitter, String eventJson) {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("SSE 任务已中断");
        }
        try {
            emitter.send(SseEmitter.event().name(EVENT_STREAM).data(eventJson));
        } catch (Exception e) {
            throw new IllegalStateException("SSE 事件发送失败", e);
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            LOGGER.debug("SSE 流关闭时已不可写: {}", e.getMessage());
        }
    }

    private String safeMessage(Exception exception) {
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "流式分析失败";
    }
}
