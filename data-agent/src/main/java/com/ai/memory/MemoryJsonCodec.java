package com.ai.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * JSON codec used by memory metadata persistence.
 *
 * @author data-agent
 */
@Component
public class MemoryJsonCodec {

    private final ObjectMapper objectMapper;

    public MemoryJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes a value to JSON.
     *
     * @param value value to serialize
     * @return JSON text
     */
    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("记忆元数据序列化失败", e);
        }
    }

    /**
     * 将 JSON 文本反序列化为字符串列表。
     *
     * @param value JSON 文本
     * @return 字符串列表，空文本或解析失败时返回空列表
     */
    public List<String> toStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    /**
     * 将 JSON 文本反序列化为普通 Map。
     *
     * @param value JSON 文本
     * @return Map 结构，空文本或解析失败时返回空 Map
     */
    public Map<String, Object> toMap(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
