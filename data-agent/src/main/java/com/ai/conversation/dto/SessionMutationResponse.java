package com.ai.conversation.dto;

/**
 * 会话变更操作的响应。
 *
 * @param success 变更是否成功
 * @param message 结果消息
 * @author data-agent
 */
public record SessionMutationResponse(boolean success, String message) {
}
