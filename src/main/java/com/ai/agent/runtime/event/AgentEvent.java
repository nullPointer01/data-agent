package com.ai.agent.runtime.event;

import com.ai.agent.runtime.AgentExecutionMode;
import com.ai.agent.runtime.AgentRunContext;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 与 HTTP/SSE 传输方式无关的 Agent Run 事件。
 *
 * @param runId 运行编号
 * @param mode 执行模式
 * @param occurredAt 事件发生时间
 * @param type 事件类型
 * @param payload 安全事件载荷
 * @author data-agent
 */
public record AgentEvent(
        String runId,
        AgentExecutionMode mode,
        Instant occurredAt,
        AgentEventType type,
        Map<String, Object> payload) {

    public AgentEvent {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("Agent Event runId 不能为空");
        }
        if (mode == null || occurredAt == null || type == null) {
            throw new IllegalArgumentException("Agent Event 公共字段不能为空");
        }
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    /**
     * 使用当前 Run 公共信息创建事件。
     *
     * @param context 运行上下文
     * @param type 事件类型
     * @param payload 事件载荷
     * @return 类型化事件
     */
    public static AgentEvent of(AgentRunContext context, AgentEventType type, Map<String, Object> payload) {
        return new AgentEvent(context.runId(), context.mode(), Instant.now(), type, payload);
    }
}
