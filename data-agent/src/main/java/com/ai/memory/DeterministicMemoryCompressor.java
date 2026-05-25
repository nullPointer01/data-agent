package com.ai.memory;

import com.ai.memory.dto.MemoryCompressionResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Rule-based memory compressor used before introducing model-based summarization.
 *
 * @author data-agent
 */
@Component
public class DeterministicMemoryCompressor implements MemoryCompressor {

    private static final int USER_PREVIEW_LENGTH = 220;
    private static final int ASSISTANT_PREVIEW_LENGTH = 260;
    private static final int LONG_TERM_PREVIEW_LENGTH = 500;

    @Override
    public MemoryCompressionResult compressConversation(String userMessage, String assistantReply) {
        String normalizedUserMessage = normalize(userMessage);
        String normalizedAssistantReply = normalize(assistantReply);
        String content = "用户: " + normalizedUserMessage + "\n助手: " + normalizedAssistantReply;
        String summary = buildSummary(normalizedUserMessage, normalizedAssistantReply);
        return new MemoryCompressionResult(content, summary, List.of(), List.of("conversation"));
    }

    @Override
    public MemoryCompressionResult compressExplicitMemory(String userMessage, MemoryType memoryType) {
        String normalizedMessage = normalize(userMessage);
        return new MemoryCompressionResult(
                normalizedMessage,
                "用户明确要求记住：" + preview(normalizedMessage, LONG_TERM_PREVIEW_LENGTH),
                List.of(),
                longTermTags(memoryType));
    }

    private String buildSummary(String userMessage, String assistantReply) {
        String userPreview = preview(userMessage, USER_PREVIEW_LENGTH);
        String assistantPreview = preview(assistantReply, ASSISTANT_PREVIEW_LENGTH);
        if (!StringUtils.hasText(userPreview)) {
            return "助手回复：" + assistantPreview;
        }
        if (!StringUtils.hasText(assistantPreview)) {
            return "用户提到：" + userPreview;
        }
        return "用户提到：" + userPreview + "；助手回复：" + assistantPreview;
    }

    private List<String> longTermTags(MemoryType memoryType) {
        if (MemoryType.PREFERENCE.equals(memoryType)) {
            return List.of("user_memory", "preference");
        }
        if (MemoryType.ENTITY.equals(memoryType)) {
            return List.of("user_memory", "profile");
        }
        return List.of("user_memory");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String preview(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }
}
