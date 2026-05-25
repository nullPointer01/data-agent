package com.ai.controller;

import com.ai.model.ConversationMessage;
import com.ai.model.ConversationSessionEntity;
import com.ai.security.auth.JwtTokenProvider;
import com.ai.security.SecurityContextHelper;
import com.ai.service.RateLimitService;
import com.ai.service.SessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ConversationSessionController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@AutoConfigureMockMvc(addFilters = false)
class ConversationSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionManager sessionManager;

    @MockBean
    private SecurityContextHelper securityContextHelper;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void createSessionReturnsSessionId() throws Exception {
        when(sessionManager.createSession()).thenReturn("session-1");

        mockMvc.perform(post("/api/v1/analysis/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sessionId").value("session-1"));
    }

    @Test
    void listSessionsReturnsCurrentUserSessions() throws Exception {
        ConversationSessionEntity session = new ConversationSessionEntity();
        session.setSessionId("session-1");
        session.setTitle("hello");
        when(securityContextHelper.getCurrentUserId()).thenReturn("user-1");
        when(sessionManager.getUserSessions("user-1")).thenReturn(List.of(session));

        mockMvc.perform(get("/api/v1/analysis/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sessions[0].sessionId").value("session-1"));
    }

    @Test
    void getSessionMessagesReturnsMessages() throws Exception {
        ConversationMessage message = new ConversationMessage();
        message.setSessionId("session-1");
        message.setRole("user");
        message.setContent("hello");
        when(sessionManager.getSessionMessages("session-1")).thenReturn(List.of(message));

        mockMvc.perform(get("/api/v1/analysis/session/session-1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages[0].content").value("hello"));
    }

    @Test
    void destroySessionDelegatesToSessionManager() throws Exception {
        mockMvc.perform(delete("/api/v1/analysis/session/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("会话已销毁"));

        verify(sessionManager).destroySession("session-1");
    }
}
