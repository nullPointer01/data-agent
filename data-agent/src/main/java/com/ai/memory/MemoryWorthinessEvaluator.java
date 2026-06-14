package com.ai.memory;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 评估对话内容是否值得存储为记忆。
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
     * 检查一轮已完成的对话是否应保留为短期记忆。
     *
     * @param userMessage 用户消息
     * @param assistantReply 助手回复
     * @return 如果该轮对话包含有用内容则返回 true
     */
    public boolean shouldStoreConversation(String userMessage, String assistantReply) {
        return StringUtils.hasText(normalize(userMessage)) || StringUtils.hasText(normalize(assistantReply));
    }

    /**
     * 检查用户是否显式请求持久化记忆捕获。
     *
     * @param userMessage 用户消息
     * @return 如果存在显式记忆意图则返回 true
     */
    public boolean hasExplicitMemoryIntent(String userMessage) {
        String normalizedMessage = normalize(userMessage).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalizedMessage)) {
            return false;
        }
        return EXPLICIT_MEMORY_KEYWORDS.stream().anyMatch(normalizedMessage::contains);
    }

    /**
     * 将用户显式记忆分类为语义记忆类型。
     *
     * @param userMessage 用户消息
     * @return 记忆类型
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
