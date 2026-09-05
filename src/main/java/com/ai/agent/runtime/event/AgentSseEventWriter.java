package com.ai.agent.runtime.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 将类型化 Agent Event 转换为兼容现有前端的 SSE JSON。
 *
 * @author data-agent
 */
@Component
public class AgentSseEventWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentSseEventWriter.class);

    private final ObjectMapper objectMapper;

    public AgentSseEventWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 为一次 SSE 请求创建带终态保护的事件接收端。
     *
     * @param emitter JSON 字符串消费者
     * @return 流式事件接收端
     */
    public AgentEventSink createSink(Consumer<String> emitter) {
        if (emitter == null) {
            throw new IllegalArgumentException("SSE emitter 不能为空");
        }
        AgentEventSink serializer = new AgentEventSink() {
            @Override
            public void emit(AgentEvent event) {
                write(emitter, event);
            }

            @Override
            public boolean isStreaming() {
                return true;
            }
        };
        return new GuardedAgentEventSink(serializer);
    }

    private void write(Consumer<String> emitter, AgentEvent event) {
        Map<String, Object> body = new LinkedHashMap<>(event.payload());
        body.put("type", legacyType(event.type()));
        body.put("runId", event.runId());
        body.put("mode", event.mode().name());
        body.put("eventTime", event.occurredAt().toString());
        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            LOGGER.warn("序列化 Agent SSE 事件失败: runId={}, type={}", event.runId(), event.type(), e);
            return;
        }
        // 传输失败必须交给上层驱动取消，不能在序列化适配器里吞掉。
        emitter.accept(serialized);
    }

    private String legacyType(AgentEventType type) {
        return switch (type) {
            case RUN_STARTED -> "run_started";
            case RAG_CONTEXT -> "rag_context";
            case THINKING_START -> "thinking_start";
            case MODEL_TOKEN -> "token";
            case TOOL_CALL -> "tool_call";
            case APPROVAL_REQUIRED -> "approval_required";
            case REFLECTION -> "reflection";
            case EXECUTION_PLAN -> "execution_plan";
            case PARALLEL_PRECHECK -> "parallel_precheck";
            case ORCHESTRATION -> "orchestration";
            case BUDGET_UPDATED -> "budget_updated";
            case ERROR -> "error";
            case RUN_PAUSED -> "done";
            case RUN_TERMINATED -> "done";
        };
    }
}
