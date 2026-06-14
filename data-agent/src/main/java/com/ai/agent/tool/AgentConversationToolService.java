package com.ai.agent.tool;

import com.ai.model.ConversationSession;
import com.ai.service.SessionManager;
import org.springframework.stereotype.Service;

/**
 * Agent 推理过程中使用的对话辅助工具。
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
     * 从当前会话历史构建紧凑的上下文提示。
     *
     * @param sessionId 会话 ID
     * @return 模型可读的对话历史
     */
    public String getConversationHistory(String sessionId) {
        ConversationSession session = sessionManager.getSession(sessionId);
        if (session == null || session.getHistory().isEmpty()) {
            return EMPTY_HISTORY_MESSAGE;
        }
        return session.buildContextPrompt("");
    }

    /**
     * 当任务描述不完整时，创建向用户请求更多信息的结构化请求。
     *
     * @param message 模型请求的缺失信息
     * @return 面向用户的澄清请求
     */
    public String askUserForInfo(String message) {
        return NEED_USER_INPUT_PREFIX + message;
    }
}
