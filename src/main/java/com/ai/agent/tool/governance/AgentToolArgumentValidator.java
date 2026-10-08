package com.ai.agent.tool.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import dev.langchain4j.internal.JsonSchemaElementJsonUtils;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
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

/**
 * 使用模型看到的同一 ToolSpecification Schema 校验不可信工具参数。
 *
 * @author data-agent
 */
@Component
public class AgentToolArgumentValidator {

    private static final JsonSchemaFactory SCHEMAS = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

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
            parsed = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(json);
        } catch (Exception e) {
            return AgentToolArgumentValidation.invalid("{length=" + json.length() + "}", "工具参数不是有效 JSON");
        }
        if (!(parsed instanceof ObjectNode objectNode)) {
            return AgentToolArgumentValidation.invalid("{type=" + (parsed == null ? "missing" : parsed.getNodeType()) + "}", "工具参数必须是 JSON 对象");
        }
        String summary = summarize(objectNode);
        try {
            ObjectNode contract = objectMapper.valueToTree(schema == null
                    ? Map.of("type", "object", "properties", Map.of()) : JsonSchemaElementJsonUtils.toMap(schema));
            // 保留本地工具原有的顶层未知字段拒绝；MCP 显式允许 additionalProperties 时尊重其合同。
            if (!contract.has("additionalProperties")) contract.put("additionalProperties", false);
            if (contract.findValues("$ref").stream().anyMatch(ref -> !ref.isTextual() || !ref.asText().startsWith("#"))) {
                return AgentToolArgumentValidation.invalid(summary, "工具 Schema 不允许外部引用");
            }
            if (!SCHEMAS.getSchema(contract).validate(objectNode).isEmpty()) {
                // 第三方校验错误可能包含参数原文，只返回固定安全说明。
                return AgentToolArgumentValidation.invalid(summary, "工具参数不符合 JSON Schema（必填字段、类型、枚举或额外字段）");
            }
        } catch (RuntimeException failure) {
            return AgentToolArgumentValidation.invalid(summary, "工具参数 Schema 无法校验");
        }
        return AgentToolArgumentValidation.valid(objectNode, summary);
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

}
