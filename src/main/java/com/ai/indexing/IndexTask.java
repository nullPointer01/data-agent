package com.ai.indexing;

/** 一次已认领版本的不可变索引快照；身份只取自持久任务，不借用调度线程登录上下文。 */
public record IndexTask(String id, Kind kind, String tenantId, String userId, String sourceId,
        String payload, long version, int attempts, String owner) {
    public enum Kind { KNOWLEDGE, FILE, MEMORY, CONVERSATION }

    /** 对话索引所需的最小加密快照；知识任务按 ID 读取最新主库。 */
    public record Conversation(String sessionId, String question, String answer) { }

    public record Status(String state, long desiredVersion, long completedVersion, int attempts) {
        public static Status unknown() { return new Status("UNKNOWN", 0, 0, 0); }
    }
}
