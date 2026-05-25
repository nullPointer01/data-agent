package com.ai.memory;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Evaluates whether conversation content is worth storing as memory.
 *
 * @author data-agent
 */
@Component
public class MemoryWorthinessEvaluator {

    private static final List<String> EXPLICIT_MEMORY_KEYWORDS = List.of(
            "记住", "请记住", "帮我记住", "以后", "我喜欢", "我偏好", "我的偏好", "我希望", "默认");
    private static final List<String> PREFERENCE_KEYWORDS = List.of(
            "喜欢", "偏好", "希望", "默认", "习惯", "优先");
    private static final List<String> ENTITY_KEYWORDS = List.of(
            "我是", "我叫", "我的公司", "我们公司", "职位", "角色", "行业");

    /**
     * Checks whether one completed conversation turn should be retained as short-term memory.
     *
     * @param userMessage user message
     * @param assistantReply assistant reply
     * @return true if the turn contains useful content
     */
    public boolean shouldStoreConversation(String userMessage, String assistantReply) {
        return StringUtils.hasText(normalize(userMessage)) || StringUtils.hasText(normalize(assistantReply));
    }

    /**
     * Checks whether user explicitly requested durable memory capture.
     *
     * @param userMessage user message
     * @return true if explicit memory intent exists
     */
    public boolean hasExplicitMemoryIntent(String userMessage) {
        String normalizedMessage = normalize(userMessage).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalizedMessage)) {
            return false;
        }
        return EXPLICIT_MEMORY_KEYWORDS.stream().anyMatch(normalizedMessage::contains);
    }

    /**
     * Classifies explicit user memory into a semantic memory type.
     *
     * @param userMessage user message
     * @return memory type
     */
    public MemoryType classifyExplicitMemory(String userMessage) {
        String normalizedMessage = normalize(userMessage);
        if (PREFERENCE_KEYWORDS.stream().anyMatch(normalizedMessage::contains)) {
            return MemoryType.PREFERENCE;
        }
        if (ENTITY_KEYWORDS.stream().anyMatch(normalizedMessage::contains)) {
            return MemoryType.ENTITY;
        }
        return MemoryType.CONCLUSION;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
