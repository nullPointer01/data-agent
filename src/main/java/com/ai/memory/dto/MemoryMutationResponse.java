package com.ai.memory.dto;

/**
 * 记忆变更命令的响应。
 *
 * @param success 命令是否成功
 * @param message 命令消息
 * @param affectedCount 受影响的记忆数
 * @author data-agent
 */
public record MemoryMutationResponse(boolean success, String message, int affectedCount) {

    /**
     * 创建成功的变更响应。
     *
     * @param message 响应消息
     * @param affectedCount 受影响的数量
     * @return 响应
     */
    public static MemoryMutationResponse success(String message, int affectedCount) {
        return new MemoryMutationResponse(true, message, affectedCount);
    }

    /**
     * 创建失败的变更响应。
     *
     * @param message 响应消息
     * @return 响应
     */
    public static MemoryMutationResponse failure(String message) {
        return new MemoryMutationResponse(false, message, 0);
    }
}
