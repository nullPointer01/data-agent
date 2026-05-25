package com.ai.agent.tool;

import com.ai.model.ConversationSession;
import com.ai.service.SessionManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentConversationToolServiceTest {

    @Test
    void getConversationHistoryReturnsEmptyMessageWhenSessionMissing() {
        SessionManager sessionManager = mock(SessionManager.class);
        AgentConversationToolService service = new AgentConversationToolService(sessionManager);

        String result = service.getConversationHistory("session-1");

        assertEquals("没有对话历史", result);
    }

    @Test
    void getConversationHistoryBuildsPromptFromSession() {
        SessionManager sessionManager = mock(SessionManager.class);
        ConversationSession session = new ConversationSession("session-1");
        session.addUserMessage("你好");
        session.addAssistantMessage("你好，请问需要分析什么？");
        when(sessionManager.getSession("session-1")).thenReturn(session);

        AgentConversationToolService service = new AgentConversationToolService(sessionManager);

        String result = service.getConversationHistory("session-1");

        assertTrue(result.contains("用户: 你好"));
        assertTrue(result.contains("助手: 你好，请问需要分析什么？"));
    }

    @Test
    void askUserForInfoPrefixesClarificationRequest() {
        AgentConversationToolService service = new AgentConversationToolService(mock(SessionManager.class));

        String result = service.askUserForInfo("请提供数据源");

        assertEquals("需要用户输入: 请提供数据源", result);
    }
}
