package com.ai.conversation.dto;

/**
 * 会话创建后返回的响应。
 *
 * @param success 创建是否成功
 * @param sessionId 会话 ID
 * @author data-agent
 */
public record SessionCreateResponse(boolean success, String sessionId) {
}
