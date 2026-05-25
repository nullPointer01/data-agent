package com.ai.conversation.dto;

import com.ai.model.ConversationMessage;

import java.util.List;

/**
 * Response containing session messages.
 *
 * @param success whether query succeeded
 * @param messages messages
 * @param message failure message
 * @author data-agent
 */
public record SessionMessagesResponse(boolean success, List<ConversationMessage> messages, String message) {

    /**
     * Creates success response.
     *
     * @param messages messages
     * @return response
     */
    public static SessionMessagesResponse ok(List<ConversationMessage> messages) {
        return new SessionMessagesResponse(true, messages, null);
    }

    /**
     * Creates failure response.
     *
     * @param message failure message
     * @return response
     */
    public static SessionMessagesResponse fail(String message) {
        return new SessionMessagesResponse(false, List.of(), message);
    }
}
