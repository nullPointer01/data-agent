package com.ai.conversation.dto;

/**
 * Response for session mutation operations.
 *
 * @param success whether mutation succeeded
 * @param message result message
 * @author data-agent
 */
public record SessionMutationResponse(boolean success, String message) {
}
