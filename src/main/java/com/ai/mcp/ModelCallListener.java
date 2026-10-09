package com.ai.mcp;

import com.ai.config.TaskContextSnapshot;
import com.ai.logging.StructuredLogger;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 官方模型钩子：每次实际 SDK 请求记录一次结果，不保存提示词、工具参数或异常原文。
 * 请求级 attributes 承载计时与身份，缓存客户端不会持有某个用户的上下文。
 * Listener 只负责观测；Token 扣账、预算和取消仍由模型治理入口强制执行。
 */
public final class ModelCallListener implements ChatModelListener {

    private static final Object OBSERVATION_KEY = new Object();
    private final StructuredLogger logger;

    public ModelCallListener(StructuredLogger logger) {
        this.logger = logger;
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        context.attributes().put(OBSERVATION_KEY,
                new Observation(TaskContextSnapshot.capture(), System.nanoTime(), new AtomicBoolean()));
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        record(context.attributes(), context.chatRequest(), context.chatResponse(), null);
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        record(context.attributes(), context.chatRequest(), null, context.error());
    }

    private void record(Map<Object, Object> attributes, ChatRequest request, ChatResponse response, Throwable error) {
        if (!(attributes.get(OBSERVATION_KEY) instanceof Observation observation)
                || !observation.completed().compareAndSet(false, true)) {
            return;
        }
        // 流式响应通常在 SDK 线程返回，必须恢复本次请求捕获的身份，而非读取该线程的 ThreadLocal。
        observation.owner().wrap(() -> {
            Map<String, Object> data = new LinkedHashMap<>();
            var run = observation.owner().run();
            data.put("runId", run == null ? null : run.runId());
            data.put("sessionId", run == null ? null : run.sessionId());
            data.put("modelName", request.modelName());
            data.put("latencyMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - observation.startedNanos()));
            data.put("success", error == null);
            data.put("errorType", error == null ? null : error.getClass().getSimpleName());
            var usage = response == null ? null : response.tokenUsage();
            // 缺失用 null 表示，不把未知用量记录成 0；计费层仍使用自己的保守估算。
            data.put("inputTokens", usage == null ? null : usage.inputTokenCount());
            data.put("outputTokens", usage == null ? null : usage.outputTokenCount());
            data.put("totalTokens", usage == null ? null : usage.totalTokenCount());
            data.put("usageReported", usage != null);
            logger.logEvent(StructuredLogger.TYPE_LLM_CALL, data);
        }).run();
    }

    private record Observation(TaskContextSnapshot owner, long startedNanos, AtomicBoolean completed) {
    }
}
