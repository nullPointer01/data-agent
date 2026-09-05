package com.ai.controller;

import com.ai.conversation.dto.SessionCreateResponse;
import com.ai.conversation.dto.SessionListResponse;
import com.ai.conversation.dto.SessionMessagesResponse;
import com.ai.conversation.dto.SessionMutationResponse;
import com.ai.security.SecurityContextHelper;
import com.ai.service.SessionManager;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对话会话接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/analysis")
public class ConversationSessionController {

    private static final String DESTROYED_MESSAGE = "会话已销毁";

    private final SessionManager sessionManager;
    private final SecurityContextHelper securityContextHelper;

    public ConversationSessionController(SessionManager sessionManager, SecurityContextHelper securityContextHelper) {
        this.sessionManager = sessionManager;
        this.securityContextHelper = securityContextHelper;
    }

    @PostMapping("/session")
    public SessionCreateResponse createSession() {
        return new SessionCreateResponse(true, sessionManager.createSession());
    }

    @DeleteMapping("/session/{sessionId}")
    public SessionMutationResponse destroySession(@PathVariable String sessionId) {
        sessionManager.destroySession(sessionId);
        return new SessionMutationResponse(true, DESTROYED_MESSAGE);
    }

    @GetMapping("/sessions")
    public SessionListResponse listSessions() {
        try {
            String userId = securityContextHelper.getCurrentUserId();
            return new SessionListResponse(true, sessionManager.getUserSessions(userId));
        } catch (Exception e) {
            return new SessionListResponse(true, List.of());
        }
    }

    @GetMapping("/session/{sessionId}/messages")
    public SessionMessagesResponse getSessionMessages(@PathVariable String sessionId) {
        try {
            return SessionMessagesResponse.ok(sessionManager.getSessionMessages(sessionId));
        } catch (Exception e) {
            return SessionMessagesResponse.fail(e.getMessage());
        }
    }
}
