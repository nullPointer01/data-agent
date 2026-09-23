package com.ai.memory;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 对话摘要准入与记忆提取调度；不决定是否允许写入长期记忆。
 *
 * @author data-agent
 */
@Component
public class MemoryWorthinessEvaluator {

    private static final Pattern SYNCHRONOUS_SAVE_COMMAND = Pattern.compile("^(?:请|帮我)?记住\\s*[:：]\\s*\\S");

    /**
     * 检查一轮已完成的对话是否应保留为短期记忆。
     *
     * @param userMessage 用户消息
     * @param assistantReply 助手回复
     * @return 如果该轮对话包含有用内容则返回 true
     */
    public boolean shouldStoreConversation(String userMessage, String assistantReply) {
        return StringUtils.hasText(userMessage) || StringUtils.hasText(assistantReply);
    }

    /**
     * 明确的保存命令格式同步提取，以便请求完成后刷新记忆中心。
     * 此匹配只控制等待时机；是否保存仍由与后台任务相同的语义准入决定。
     *
     * @param userMessage 用户消息
     * @return 是否等待本轮提取完成
     */
    public boolean requiresSynchronousExtraction(String userMessage) {
        return userMessage != null && SYNCHRONOUS_SAVE_COMMAND.matcher(userMessage.strip()).find();
    }
}
