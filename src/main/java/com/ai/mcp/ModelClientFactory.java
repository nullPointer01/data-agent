package com.ai.mcp;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * OpenAI-compatible LangChain4j 客户端的无状态构建工厂。
 *
 * @author data-agent
 */
@Component
public class ModelClientFactory {

    private static final String KEY_NOT_REQUIRED = "not-required";

    public ChatModel createChatModel(ResolvedModelEndpoint endpoint, Double temperature,
            Integer maxTokens, Duration timeout) {
        var builder = OpenAiChatModel.builder()
                .apiKey(clientApiKey(endpoint))
                .modelName(endpoint.modelName())
                .baseUrl(endpoint.baseUrl())
                // 只影响携带 JSON Schema 的请求；普通文本、JSON Object 和工具协议不变。
                .strictJsonSchema(true)
                .maxRetries(0)
                .timeout(timeout);
        if (temperature != null) {
            builder.temperature(temperature);
        }
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        return builder.build();
    }

    public StreamingChatModel createStreamingChatModel(ResolvedModelEndpoint endpoint, Double temperature,
            Integer maxTokens, Duration timeout) {
        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(clientApiKey(endpoint))
                .modelName(endpoint.modelName())
                .baseUrl(endpoint.baseUrl())
                .timeout(timeout);
        if (temperature != null) {
            builder.temperature(temperature);
        }
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        return builder.build();
    }

    private String clientApiKey(ResolvedModelEndpoint endpoint) {
        return endpoint.hasApiKey() ? endpoint.apiKey() : KEY_NOT_REQUIRED;
    }
}
