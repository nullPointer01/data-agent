package com.ai.agent.runtime.event;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        emitter.accept(encode(event));
    }

    /** HTTP 与持久日志共用同一事件协议，编码失败不能静默遗漏。 */
    public String encode(AgentEvent event) {
        Map<String, Object> body = new LinkedHashMap<>(event.payload());
        body.put("type", legacyType(event.type()));
        body.put("runId", event.runId());
        body.put("mode", event.mode().name());
        body.put("eventTime", event.occurredAt().toString());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Agent 事件编码失败", e);
        }
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
            case CONTEXT_GOVERNED -> "context_governed";
            case BUDGET_UPDATED -> "budget_updated";
            case ERROR -> "error";
            case RUN_PAUSED -> "done";
            case RUN_TERMINATED -> "done";
        };
    }
}
