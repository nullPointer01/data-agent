package com.ai.agent.capability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent Profile 统一能力字段的唯一 JSON 编解码边界。
 *
 * @author data-agent
 */
@Component
public class AgentCapabilityConfigurationCodec {

    public static final String CAPABILITY_BINDINGS_FIELD = "capability_bindings";

    private final ObjectMapper objectMapper;

    public AgentCapabilityConfigurationCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解码统一能力字段。SQL NULL 属于未完成迁移的非法状态，[] 表示显式零能力。
     *
     * @param rawValue 数据库存储的 JSON 数组
     * @param agentId Agent 编号
     * @return 规范化且顺序稳定的能力身份
     */
    public List<String> decodeCapabilityBindings(String rawValue, String agentId) {
        if (rawValue == null) {
            throw invalid(CAPABILITY_BINDINGS_FIELD, agentId, "MISSING_REQUIRED_CONFIGURATION", null);
        }
        if (rawValue.isBlank()) {
            throw invalid(CAPABILITY_BINDINGS_FIELD, agentId, "BLANK_JSON", null);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawValue);
        } catch (JsonProcessingException exception) {
            throw invalid(CAPABILITY_BINDINGS_FIELD, agentId, "MALFORMED_JSON", exception);
        }
        if (root == null || !root.isArray()) {
            throw invalid(CAPABILITY_BINDINGS_FIELD, agentId, "JSON_ARRAY_REQUIRED", null);
        }

        List<String> rawValues = new ArrayList<>(root.size());
        for (JsonNode item : root) {
            if (!item.isTextual()) {
                throw invalid(CAPABILITY_BINDINGS_FIELD, agentId, "STRING_ENTRY_REQUIRED", null);
            }
            rawValues.add(item.textValue());
        }
        return normalizeValues(rawValues, agentId);
    }

    /**
     * 编码统一绑定。空列表持久化为 []，null 请求直接拒绝。
     *
     * @param values 稳定能力身份
     * @return JSON 数组
     */
    public String encodeCapabilityBindings(List<String> values) {
        if (values == null) {
            throw invalid(CAPABILITY_BINDINGS_FIELD, null, "MISSING_REQUIRED_CONFIGURATION", null);
        }
        return encode(normalizeValues(values, null));
    }

    private List<String> normalizeValues(List<String> values, String agentId) {
        if (values.size() > AgentCapabilityBindingSet.MAX_BINDINGS) {
            throw invalid(CAPABILITY_BINDINGS_FIELD, agentId, "TOO_MANY_ENTRIES", null);
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw invalid(CAPABILITY_BINDINGS_FIELD, agentId, "BLANK_ENTRY", null);
            }
            String normalizedValue = value.trim();
            if (!normalized.add(normalizedValue)) {
                throw invalid(CAPABILITY_BINDINGS_FIELD, agentId, "DUPLICATE_ENTRY", null);
            }
        }
        try {
            return AgentCapabilityBindingSet.of(List.copyOf(normalized)).identities();
        } catch (IllegalArgumentException exception) {
            throw invalid(CAPABILITY_BINDINGS_FIELD, agentId, "INVALID_CAPABILITY_IDENTITY", exception);
        }
    }

    private String encode(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw invalid(CAPABILITY_BINDINGS_FIELD, null, "SERIALIZATION_FAILED", exception);
        }
    }

    private AgentCapabilityConfigurationException invalid(
            String field, String agentId, String reasonCode, Throwable cause) {
        return new AgentCapabilityConfigurationException(field, agentId, reasonCode, cause);
    }

}
