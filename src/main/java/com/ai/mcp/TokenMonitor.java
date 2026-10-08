package com.ai.mcp;

import com.ai.agent.context.AgentContextProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统一 Token 估算入口：显式映射的模型复用框架 tokenizer；未知词表按 UTF-8 字节保守预留。
 * 估算用于窗口与准入，实际计费仍优先使用厂商返回的 TokenUsage。
 */
@Service
public class TokenMonitor implements TokenCountEstimator {
    private final Map<String, TokenCountEstimator> estimators;

    public TokenMonitor(AgentContextProperties properties) {
        Map<String, TokenCountEstimator> byName = new java.util.HashMap<>();
        estimators = properties.getModelTokenizers().entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> byName.computeIfAbsent(entry.getValue(), OpenAiTokenCountEstimator::new)));
    }

    public long estimateTokens(String text) { return estimateTokenCountInText(text); }

    public long estimateTokens(String text, String modelId) {
        return estimator(modelId).estimateTokenCountInText(text == null ? "" : text);
    }

    public long estimateMessage(ChatMessage message, String modelId) {
        return estimator(modelId).estimateTokenCountInMessage(message);
    }

    /** 用项目模型 ID 显式选词表，不能仅凭兼容 URL 猜测实际模型。 */
    public TokenCountEstimator estimator(String modelId) {
        return estimators.getOrDefault(modelId == null || modelId.isBlank() ? "default" : modelId, this);
    }

    @Override
    public int estimateTokenCountInText(String text) {
        return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        // 包括角色、工具名称/参数等协议开销，不能只统计 assistant 可见文本。
        return message == null ? 0 : Math.addExact(8, estimateTokenCountInText(message.toString()));
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) total = Math.addExact(total, estimateTokenCountInMessage(message));
        return total;
    }
}
