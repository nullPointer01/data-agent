package com.ai.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 输出便于检索和聚合的 JSON 结构化日志。
 *
 * @author data-agent
 */
@Component
public class StructuredLogger {

    public static final String TYPE_AGENT_STEP = "AGENT_STEP";
    public static final String TYPE_TOOL_CALL = "TOOL_CALL";
    public static final String TYPE_LLM_CALL = "LLM_CALL";
    public static final String TYPE_AGENT_FEEDBACK = "AGENT_FEEDBACK";
    public static final String TYPE_AGENT_TRACE = "AGENT_TRACE";

    private static final Logger LOGGER = LoggerFactory.getLogger(StructuredLogger.class);
    private static final int MAX_VALUE_LENGTH = 2048;
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_TYPE = "type";
    private static final String KEY_REQUEST_ID = "requestId";
    private static final String KEY_METHOD = "method";
    private static final String KEY_PATH = "path";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_TENANT_ID = "tenantId";

    private final ObjectMapper objectMapper;

    public StructuredLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 记录 Agent 执行步骤。
     *
     * @param sessionId 会话编号
     * @param step 步骤名称
     * @param data 结构化数据
     */
    public void logAgentStep(String sessionId, String step, Map<String, Object> data) {
        Map<String, Object> payload = basePayload(TYPE_AGENT_STEP);
        payload.put("sessionId", sessionId);
        payload.put("step", step);
        payload.put("data", sanitizeMap(data));
        write(payload);
    }

    /**
     * 记录工具调用。
     *
     * @param sessionId 会话编号
     * @param toolName 工具名称
     * @param params 工具参数
     * @param result 工具返回或错误
     * @param executionTimeMs 执行耗时
     * @param success 是否成功
     */
    public void logToolCall(String sessionId,
            String toolName,
            Map<String, Object> params,
            Object result,
            long executionTimeMs,
            boolean success) {
        Map<String, Object> payload = basePayload(TYPE_TOOL_CALL);
        payload.put("sessionId", sessionId);
        payload.put("toolName", toolName);
        payload.put("params", sanitizeMap(params));
        payload.put("result", sanitizeValue(result));
        payload.put("executionTimeMs", executionTimeMs);
        payload.put("success", success);
        write(payload);
    }

    /**
     * 记录模型调用。
     *
     * @param sessionId 会话编号
     * @param modelId 模型编号
     * @param inputTokens 输入 token 估算
     * @param outputTokens 输出 token 估算
     * @param latencyMs 调用耗时
     * @param success 是否成功
     * @param error 错误信息
     */
    public void logLlmCall(String sessionId,
            String modelId,
            long inputTokens,
            long outputTokens,
            long latencyMs,
            boolean success,
            String error) {
        Map<String, Object> payload = basePayload(TYPE_LLM_CALL);
        payload.put("sessionId", sessionId);
        payload.put("modelId", modelId);
        payload.put("inputTokens", inputTokens);
        payload.put("outputTokens", outputTokens);
        payload.put("totalTokens", inputTokens + outputTokens);
        payload.put("latencyMs", latencyMs);
        payload.put("success", success);
        payload.put("error", truncate(error));
        write(payload);
    }

    /**
     * 记录通用事件。
     *
     * @param type 事件类型
     * @param data 事件数据
     */
    public void logEvent(String type, Map<String, Object> data) {
        Map<String, Object> payload = basePayload(type);
        payload.put("data", sanitizeMap(data));
        write(payload);
    }

    private Map<String, Object> basePayload(String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(KEY_TIMESTAMP, Instant.now().toString());
        payload.put(KEY_TYPE, type);
        putMdcValue(payload, KEY_REQUEST_ID, RequestCorrelationContext.MDC_REQUEST_ID);
        putMdcValue(payload, KEY_METHOD, RequestCorrelationContext.MDC_METHOD);
        putMdcValue(payload, KEY_PATH, RequestCorrelationContext.MDC_PATH);
        putMdcValue(payload, KEY_USER_ID, RequestCorrelationContext.MDC_USER_ID);
        putMdcValue(payload, KEY_USERNAME, RequestCorrelationContext.MDC_USERNAME);
        putMdcValue(payload, KEY_TENANT_ID, RequestCorrelationContext.MDC_TENANT_ID);
        return payload;
    }

    private void putMdcValue(Map<String, Object> payload, String key, String mdcKey) {
        String value = MDC.get(mdcKey);
        if (value != null && !value.isBlank()) {
            payload.put(key, truncate(value));
        }
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        data.forEach((key, value) -> sanitized.put(key, sanitizeValue(value)));
        return sanitized;
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof String text) {
            return truncate(text);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> sanitized.put(String.valueOf(key), sanitizeValue(nestedValue)));
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.List<Object> sanitized = new java.util.ArrayList<>();
            for (Object item : iterable) {
                sanitized.add(sanitizeValue(item));
            }
            return sanitized;
        }
        return value;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_VALUE_LENGTH);
    }

    private void write(Map<String, Object> payload) {
        try {
            LOGGER.info(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            LOGGER.warn("结构化日志序列化失败: {}", e.getMessage());
        }
    }
}
