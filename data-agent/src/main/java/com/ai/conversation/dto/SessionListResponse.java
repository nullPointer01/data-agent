package com.ai.conversation.dto;

import com.ai.model.ConversationSessionEntity;

import java.util.List;

/**
 * Response containing current user's sessions.
 *
 * @param success whether query succeeded
 * @param sessions sessions
 * @author data-agent
 */
public record SessionListResponse(boolean success, List<ConversationSessionEntity> sessions) {
}
