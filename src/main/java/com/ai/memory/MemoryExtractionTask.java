package com.ai.memory;

/** 持久化记忆提取任务；payload 为加密原话，来源消息与修订号用于拒绝迟到写入。 */
public record MemoryExtractionTask(String id, String tenantId, String userId, String sessionId,
        String modelId, long sourceMessageId, long revision, String payload, int attempts, String owner) {
    /** 只保存提取需要的这一轮，不把整段历史、模型凭据或登录令牌存入任务。 */
    public record Input(String userMessage, String assistantReply) { }
}
