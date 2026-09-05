package com.ai.agent.tool.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 使用模型看到的同一 ToolSpecification Schema 校验不可信工具参数。
 *
 * @author data-agent
 */
@Component
public class AgentToolArgumentValidator {

    private final ObjectMapper objectMapper;
    private final AgentToolGovernanceProperties properties;

    public AgentToolArgumentValidator(ObjectMapper objectMapper, AgentToolGovernanceProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public AgentToolArgumentValidation validate(String argumentsJson, JsonObjectSchema schema) {
        String json = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
        if (json.length() > properties.getMaxArgumentLength()) {
            return AgentToolArgumentValidation.invalid("{length=" + json.length() + "}", "工具参数超过服务端长度上限");
        }
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(json);
        } catch (Exception e) {
            return AgentToolArgumentValidation.invalid("{length=" + json.length() + "}", "工具参数不是有效 JSON");
        }
        if (!(parsed instanceof ObjectNode objectNode)) {
            return AgentToolArgumentValidation.invalid("{type=" + parsed.getNodeType() + "}", "工具参数必须是 JSON 对象");
        }
        String summary = summarize(objectNode);
        Map<String, JsonSchemaElement> declared = schema == null || schema.properties() == null
                ? Map.of()
                : schema.properties();
        Set<String> actual = new java.util.LinkedHashSet<>();
        objectNode.fieldNames().forEachRemaining(actual::add);
        Set<String> unknown = actual.stream()
                .filter(name -> !declared.containsKey(name))
                .collect(java.util.stream.Collectors.toSet());
        if (!unknown.isEmpty()) {
            return AgentToolArgumentValidation.invalid(summary, "工具参数包含未知字段: " + sorted(unknown));
        }
        List<String> required = schema == null || schema.required() == null ? List.of() : schema.required();
        List<String> missing = required.stream()
                .filter(name -> !objectNode.has(name) || objectNode.get(name).isNull())
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            return AgentToolArgumentValidation.invalid(summary, "工具参数缺少必填字段: " + missing);
        }
        for (Map.Entry<String, JsonSchemaElement> entry : declared.entrySet()) {
            JsonNode value = objectNode.get(entry.getKey());
            if (value != null && !value.isNull() && !isCompatible(value, entry.getValue())) {
                return AgentToolArgumentValidation.invalid(summary, "工具参数字段类型错误: " + entry.getKey());
            }
        }
        return AgentToolArgumentValidation.valid(objectNode, summary);
    }

    private boolean isCompatible(JsonNode value, JsonSchemaElement schema) {
        if (schema instanceof JsonStringSchema || schema instanceof JsonEnumSchema) {
            return value.isTextual();
        }
        if (schema instanceof JsonIntegerSchema) {
            return value.isIntegralNumber();
        }
        if (schema instanceof JsonNumberSchema) {
            return value.isNumber();
        }
        if (schema instanceof JsonBooleanSchema) {
            return value.isBoolean();
        }
        return false;
    }

    private String summarize(ObjectNode arguments) {
        List<String> fields = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> entries = arguments.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String value = entry.getValue() == null ? "null" : entry.getValue().toString();
            fields.add(entry.getKey() + "{length=" + value.length() + ",sha256=" + digest(value) + "}");
        }
        fields.sort(Comparator.naturalOrder());
        return "{" + String.join(",", fields) + "}";
    }

    private String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }

    private List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }
}
