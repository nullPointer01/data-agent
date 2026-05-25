package com.ai.conversation.dto;

/**
 * Response returned after a session is created.
 *
 * @param success whether creation succeeded
 * @param sessionId session id
 * @author data-agent
 */
public record SessionCreateResponse(boolean success, String sessionId) {
}
