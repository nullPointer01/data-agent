package com.ai.memory;

import com.ai.mcp.McpModelService;
import com.ai.memory.dto.MemoryCompressionResult;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 带模型摘要能力的记忆压缩器，模型不可用时回退到规则摘要。
 *
 * @author data-agent
 */
@Primary
@Component
public class ModelBackedMemoryCompressor implements MemoryCompressor {

    private static final String MODEL_ERROR_PREFIX = "模型调用失败";
    private static final int MAX_MODEL_SUMMARY_LENGTH = 600;

    private final McpModelService modelService;
    private final DeterministicMemoryCompressor fallbackCompressor;
    private final MemoryProperties memoryProperties;

    public ModelBackedMemoryCompressor(McpModelService modelService,
            DeterministicMemoryCompressor fallbackCompressor,
            MemoryProperties memoryProperties) {
        this.modelService = modelService;
        this.fallbackCompressor = fallbackCompressor;
        this.memoryProperties = memoryProperties;
    }

    @Override
    public MemoryCompressionResult compressConversation(String userMessage, String assistantReply) {
        MemoryCompressionResult fallback = fallbackCompressor.compressConversation(userMessage, assistantReply);
        if (!memoryProperties.isModelCompressionEnabled()) {
            return fallback;
        }
        String summary = callModel(buildConversationPrompt(userMessage, assistantReply));
        if (!isUsableSummary(summary)) {
            return fallback;
        }
        return new MemoryCompressionResult(fallback.content(), limit(summary), fallback.keyEntities(),
                fallback.topicTags());
    }

    @Override
    public MemoryCompressionResult compressExplicitMemory(String userMessage, MemoryType memoryType) {
        MemoryCompressionResult fallback = fallbackCompressor.compressExplicitMemory(userMessage, memoryType);
        if (!memoryProperties.isModelCompressionEnabled()) {
            return fallback;
        }
        String summary = callModel(buildExplicitMemoryPrompt(userMessage, memoryType));
        if (!isUsableSummary(summary)) {
            return fallback;
        }
        return new MemoryCompressionResult(fallback.content(), limit(summary), fallback.keyEntities(),
                fallback.topicTags());
    }

    private String buildConversationPrompt(String userMessage, String assistantReply) {
        return """
                请把下面一轮对话压缩为可复用的用户记忆，保留事实、偏好、业务上下文和结论。
                要求：中文，80-180字，不要编造，不要输出解释。

                用户：%s
                助手：%s
                """.formatted(userMessage, assistantReply);
    }

    private String buildExplicitMemoryPrompt(String userMessage, MemoryType memoryType) {
        return """
                用户明确要求系统记住下面内容。请压缩为长期记忆，保留用户偏好、身份或结论。
                类型：%s
                要求：中文，60-160字，不要编造，不要输出解释。

                用户原文：%s
                """.formatted(memoryType, userMessage);
    }

    private String callModel(String prompt) {
        try {
            return modelService.callModel(prompt);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isUsableSummary(String value) {
        return StringUtils.hasText(value) && !value.startsWith(MODEL_ERROR_PREFIX);
    }

    private String limit(String value) {
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_MODEL_SUMMARY_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_MODEL_SUMMARY_LENGTH);
    }
}
