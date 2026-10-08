package com.ai.mcp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

/**
 * 类型化结构化任务的统一工厂：框架根据返回类型生成 Schema，并负责调用及返回值转换。
 * 严格合同作为框架 OutputGuardrail 接入；禁止宽松强转和自动修补，业务证据校验留在调用方。
 */
@Component
public class StructuredAssistantFactory {

    private static final int MAX_RESPONSE_CHARS = 8000;
    private static final ObjectMapper CONTRACT_MAPPER = strictMapper();
    private final McpModelService modelService;

    public StructuredAssistantFactory(McpModelService modelService) {
        this.modelService = modelService;
    }

    /** 每个助手绑定本次模型与预算作用域，无会话窗口，不缓存用户输入。 */
    public <T> T create(Class<T> assistantType, Class<?> resultType, String modelId,
            boolean governRun, boolean nativeSchema) {
        return AiServices.builder(assistantType)
                .chatModel(modelService.structuredChatModel(modelId, governRun, nativeSchema))
                .outputGuardrailsConfig(OutputGuardrailsConfig.builder().maxRetries(0).build())
                .outputGuardrails(new OutputGuardrail() {
                    @Override
                    public OutputGuardrailResult validate(AiMessage message) {
                        String text = message.text();
                        if (text == null || text.isBlank() || text.length() > MAX_RESPONSE_CHARS) {
                            return fatal("结构化响应为空或超长");
                        }
                        try {
                            Object value = CONTRACT_MAPPER.readerFor(resultType).readValue(text);
                            if (value == null) {
                                return fatal("结构化响应不能为 null");
                            }
                            // 使用框架提供的类型化 Guardrail 结果，避免严格校验后再宽松解析一遍。
                            return successWith(text, value);
                        } catch (Exception exception) {
                            // 不携带可能包含用户原文的解析异常；失败不自动重发模型请求。
                            return fatal("结构化响应不符合类型合同");
                        }
                    }
                }).build();
    }

    private static ObjectMapper strictMapper() {
        JsonMapper mapper = JsonMapper.builder()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                        DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper;
    }
}
