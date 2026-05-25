package com.ai.agent.tool;

import com.ai.model.ConversationSession;
import com.ai.service.SessionManager;
import org.springframework.stereotype.Service;

/**
 * Conversation helper tools used during agent reasoning.
 *
 * @author data-agent
 */
@Service
public class AgentConversationToolService {

    private static final String EMPTY_HISTORY_MESSAGE = "没有对话历史";
    private static final String NEED_USER_INPUT_PREFIX = "需要用户输入: ";

    private final SessionManager sessionManager;

    public AgentConversationToolService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Builds a compact context prompt from the current session history.
     *
     * @param sessionId conversation session id
     * @return model-readable conversation history
     */
    public String getConversationHistory(String sessionId) {
        ConversationSession session = sessionManager.getSession(sessionId);
        if (session == null || session.getHistory().isEmpty()) {
            return EMPTY_HISTORY_MESSAGE;
        }
        return session.buildContextPrompt("");
    }

    /**
     * Creates a structured request for more user input when a task is under-specified.
     *
     * @param message missing information requested by the model
     * @return user-facing clarification request
     */
    public String askUserForInfo(String message) {
        return NEED_USER_INPUT_PREFIX + message;
    }
}
