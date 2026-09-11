package com.ai.agent.react;

import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentRunEventBridge;
import com.ai.agent.runtime.event.AgentEventType;
import com.ai.rag.dto.RagCitation;
import com.ai.rag.dto.RagContextResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 为 SSE 传输层序列化 ReAct 流式事件。
 *
 * @author data-agent
 */
@Component
public class ReActStreamEventWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReActStreamEventWriter.class);
    private static final String KEY_TYPE = "type";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_ITERATION = "iteration";
    private static final String KEY_HIT_COUNT = "hitCount";
    private static final String KEY_CITATIONS = "citations";
    private static final String KEY_RETRIEVAL_AVAILABLE = "retrievalAvailable";
    private static final String KEY_SESSION_ID = "sessionId";
    private static final String KEY_TRACE_ID = "traceId";
    private static final String KEY_TITLE = "title";
    private static final String TYPE_RAG_CONTEXT = "rag_context";
    private static final String TYPE_THINKING_START = "thinking_start";
    private static final String TYPE_TOKEN = "token";
    private static final String TYPE_REFLECTION = "reflection";
    private static final String TYPE_EXECUTION_PLAN = "execution_plan";
    private static final String TYPE_PARALLEL_PRECHECK = "parallel_precheck";
    private static final String TYPE_ORCHESTRATION = "orchestration";
    private static final String TYPE_DONE = "done";
    private static final String TYPE_ERROR = "error";
    private static final String EMPTY_VALUE = "";

    private final ObjectMapper objectMapper;

    public ReActStreamEventWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 发送 RAG 上下文通知。
     *
     * @param emitter 事件消费者
     * @param hitCount 命中的上下文数量
     */
    public void emitRagContext(Consumer<String> emitter, int hitCount) {
        emitRagContext(emitter, hitCount, List.of());
    }

    /**
     * 发送包含引用的 RAG 上下文通知。
     *
     * @param emitter 事件消费者
     * @param hitCount 命中的上下文数量
     * @param citations 命中的引用
     */
    public void emitRagContext(Consumer<String> emitter, int hitCount, List<RagCitation> citations) {
        emitRagContext(emitter, hitCount, citations, true);
    }

    /**
     * 发送完整 RAG 检索结果，显式区分零命中和检索服务降级。
     */
    public void emitRagContext(Consumer<String> emitter, RagContextResponse response) {
        RagContextResponse safeResponse = response == null ? RagContextResponse.unavailable() : response;
        emitRagContext(emitter, safeResponse.getHitCount(), safeResponse.getCitations(),
                safeResponse.isRetrievalAvailable());
    }

    private void emitRagContext(Consumer<String> emitter, int hitCount, List<RagCitation> citations,
            boolean retrievalAvailable) {
        emitProcess(emitter, AgentEventType.RAG_CONTEXT, Map.of(
                KEY_TYPE, TYPE_RAG_CONTEXT,
                KEY_HIT_COUNT, hitCount,
                KEY_RETRIEVAL_AVAILABLE, retrievalAvailable,
                KEY_CITATIONS, citations == null ? List.of() : citations));
    }

    /**
     * 发送一次推理迭代开始事件。
     *
     * @param emitter 事件消费者
     * @param iteration 当前迭代次数
     */
    public void emitThinkingStart(Consumer<String> emitter, int iteration) {
        emitProcess(emitter, AgentEventType.THINKING_START,
                Map.of(KEY_TYPE, TYPE_THINKING_START, KEY_ITERATION, iteration));
    }

    /**
     * 发送一个流式模型 token。
     *
     * @param emitter 事件消费者
     * @param token token 文本
     */
    public void emitToken(Consumer<String> emitter, String token) {
        emitProcess(emitter, AgentEventType.MODEL_TOKEN,
                Map.of(KEY_TYPE, TYPE_TOKEN, KEY_CONTENT, token == null ? EMPTY_VALUE : token));
    }

    /**
     * 在工具失败后发送一次自我反思事件。
     *
     * @param emitter 事件消费者
     * @param content 反思内容
     */
    public void emitReflection(Consumer<String> emitter, String content) {
        emitProcess(emitter, AgentEventType.REFLECTION,
                Map.of(KEY_TYPE, TYPE_REFLECTION, KEY_CONTENT, content == null ? EMPTY_VALUE : content));
    }

    /**
     * 发送增强推理执行计划事件。
     *
     * @param emitter 事件消费者
     * @param content 执行计划内容
     */
    public void emitExecutionPlan(Consumer<String> emitter, String content) {
        emitProcess(emitter, AgentEventType.EXECUTION_PLAN, Map.of(
                KEY_TYPE, TYPE_EXECUTION_PLAN,
                KEY_TITLE, "执行计划",
                KEY_CONTENT, content == null ? EMPTY_VALUE : content));
    }

    /**
     * 发送并行预检结果事件。
     *
     * @param emitter 事件消费者
     * @param content 并行预检内容
     */
    public void emitParallelPrecheck(Consumer<String> emitter, String content) {
        emitProcess(emitter, AgentEventType.PARALLEL_PRECHECK, Map.of(
                KEY_TYPE, TYPE_PARALLEL_PRECHECK,
                KEY_TITLE, "并行预检",
                KEY_CONTENT, content == null ? EMPTY_VALUE : content));
    }

    /**
     * 发送编排轨迹事件。
     *
     * @param emitter 事件消费者
     * @param title 轨迹标题
     * @param content 轨迹内容
     */
    public void emitOrchestration(Consumer<String> emitter, String title, String content) {
        emitProcess(emitter, AgentEventType.ORCHESTRATION, Map.of(
                KEY_TYPE, TYPE_ORCHESTRATION,
                KEY_TITLE, title == null ? EMPTY_VALUE : title,
                KEY_CONTENT, content == null ? EMPTY_VALUE : content));
    }

    /**
     * 发送流式结束事件。
     *
     * @param emitter 事件消费者
     * @param sessionId 会话 ID
     */
    public void emitDone(Consumer<String> emitter, String sessionId) {
        emitDone(emitter, sessionId, null);
    }

    /**
     * 发送包含执行轨迹编号的流式结束事件。
     *
     * @param emitter 事件消费者
     * @param sessionId 会话 ID
     * @param traceId 执行轨迹 ID
     */
    public void emitDone(Consumer<String> emitter, String sessionId, String traceId) {
        // 统一 Run 内的终态只允许 AgentRunCoordinator 发布。
        if (AgentRunScope.current().isPresent()) {
            return;
        }
        emit(emitter, Map.of(
                KEY_TYPE, TYPE_DONE,
                KEY_SESSION_ID, sessionId != null ? sessionId : EMPTY_VALUE,
                KEY_TRACE_ID, traceId != null ? traceId : EMPTY_VALUE));
    }

    /**
     * 发送流式错误事件。
     *
     * @param emitter 事件消费者
     * @param message 错误信息
     */
    public void emitError(Consumer<String> emitter, String message) {
        emitProcess(emitter, AgentEventType.ERROR,
                Map.of(KEY_TYPE, TYPE_ERROR, KEY_CONTENT, message == null ? EMPTY_VALUE : message));
    }

    private void emitProcess(Consumer<String> emitter, AgentEventType eventType, Map<String, Object> legacyEvent) {
        AgentRunContext context = resolveRunContext(emitter);
        if (context == null) {
            emit(emitter, legacyEvent);
            return;
        }
        if (context.snapshot().status().isTerminal()) {
            return;
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>(legacyEvent);
        payload.remove(KEY_TYPE);
        context.eventSink().emit(AgentEvent.of(context, eventType, payload));
    }

    private AgentRunContext resolveRunContext(Consumer<String> emitter) {
        AgentRunContext scopedContext = AgentRunScope.current().orElse(null);
        if (scopedContext != null) {
            return scopedContext;
        }
        return emitter instanceof AgentRunEventBridge bridge ? bridge.context() : null;
    }

    private void emit(Consumer<String> emitter, Map<String, Object> event) {
        try {
            emitter.accept(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            LOGGER.warn("序列化 ReAct 流式事件失败", e);
        }
    }
}
