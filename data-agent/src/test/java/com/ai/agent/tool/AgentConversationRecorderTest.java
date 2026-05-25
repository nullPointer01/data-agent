package com.ai.agent.tool;

import com.ai.mcp.TokenMonitor;
import com.ai.memory.ConversationMemoryCaptureService;
import com.ai.model.AnalysisRequest;
import com.ai.model.ConversationSession;
import com.ai.security.SecurityContextHelper;
import com.ai.service.SessionManager;
import com.ai.service.VectorMemoryService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentConversationRecorderTest {

    @Test
    void recordReActConversationPersistsMessagesAndIndexesMemory() {
        TokenMonitor tokenMonitor = mock(TokenMonitor.class);
        SessionManager sessionManager = mock(SessionManager.class);
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        ConversationMemoryCaptureService conversationMemoryCaptureService = mock(ConversationMemoryCaptureService.class);
        ConversationSession session = new ConversationSession("session-1");
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("hello");

        when(tokenMonitor.estimateTokens("hello")).thenReturn(2L);
        when(tokenMonitor.estimateTokens("world")).thenReturn(3L);
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(securityContextHelper.getCurrentUserId()).thenReturn("user-1");

        AgentConversationRecorder recorder = new AgentConversationRecorder(
                tokenMonitor, sessionManager, vectorMemoryService, securityContextHelper, conversationMemoryCaptureService);

        recorder.recordReActConversation(session, request, "world", "model-1");

        verify(sessionManager).saveMessage("session-1", "user", "hello", null, null, 2L);
        verify(sessionManager).saveMessage("session-1", "assistant", "world", "react-agent", "model-1", 3L);
        verify(conversationMemoryCaptureService).captureCompletedConversation("session-1", "hello", "world");
        verify(vectorMemoryService).indexConversation("session-1", "hello", "world", "tenant-1", "user-1");
    }

    @Test
    void recordReActConversationIndexesAnonymousConversationWithoutSession() {
        TokenMonitor tokenMonitor = mock(TokenMonitor.class);
        SessionManager sessionManager = mock(SessionManager.class);
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        ConversationMemoryCaptureService conversationMemoryCaptureService = mock(ConversationMemoryCaptureService.class);
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("hello");
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(securityContextHelper.getCurrentUserId()).thenReturn("user-1");

        AgentConversationRecorder recorder = new AgentConversationRecorder(
                tokenMonitor, sessionManager, vectorMemoryService, securityContextHelper, conversationMemoryCaptureService);

        recorder.recordReActConversation(null, request, "world", null);

        verify(conversationMemoryCaptureService).captureCompletedConversation("anonymous", "hello", "world");
        verify(vectorMemoryService).indexConversation(eq("anonymous"), eq("hello"), anyString(), eq("tenant-1"),
                eq("user-1"));
    }
}
