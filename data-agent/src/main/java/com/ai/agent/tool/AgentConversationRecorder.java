package com.ai.agent.tool;

import com.ai.mcp.TokenMonitor;
import com.ai.memory.ConversationMemoryCaptureService;
import com.ai.model.AnalysisRequest;
import com.ai.model.ConversationSession;
import com.ai.security.SecurityContextHelper;
import com.ai.service.SessionManager;
import com.ai.service.VectorMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 持久化 Agent 对话记录并索引长期记忆。
 *
 * @author data-agent
 */
@Component
public class AgentConversationRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentConversationRecorder.class);
    private static final int ASSISTANT_MEMORY_PREVIEW_LENGTH = 500;
    private static final String ANONYMOUS_SESSION_ID = "anonymous";
    private static final String USER_ROLE = "user";
    private static final String ASSISTANT_ROLE = "assistant";
    private static final String REACT_SKILL_NAME = "react-agent";

    private final TokenMonitor tokenMonitor;
    private final SessionManager sessionManager;
    private final VectorMemoryService vectorMemoryService;
    private final SecurityContextHelper securityContextHelper;
    private final ConversationMemoryCaptureService conversationMemoryCaptureService;

    public AgentConversationRecorder(TokenMonitor tokenMonitor,
            SessionManager sessionManager,
            VectorMemoryService vectorMemoryService,
            SecurityContextHelper securityContextHelper,
            ConversationMemoryCaptureService conversationMemoryCaptureService) {
        this.tokenMonitor = tokenMonitor;
        this.sessionManager = sessionManager;
        this.vectorMemoryService = vectorMemoryService;
        this.securityContextHelper = securityContextHelper;
        this.conversationMemoryCaptureService = conversationMemoryCaptureService;
    }

    /**
     * 记录已完成的 ReAct 对话。
     *
     * @param session 会话对象
     * @param request 原始请求
     * @param result 最终回答
     * @param modelId 选中的模型 ID
     */
    public void recordReActConversation(ConversationSession session, AnalysisRequest request, String result,
            String modelId) {
        persistConversation(session, request, result, REACT_SKILL_NAME, modelId);
        captureConversationMemory(session, request, result);
        indexConversation(session, request, result);
    }

    /**
     * 记录会话范围内的命令或技能对话，不进行原始对话的向量索引。
     *
     * @param session 会话对象
     * @param request 原始请求
     * @param result 最终回答
     * @param skillUsed 使用的技能或命令名称
     * @param modelId 选中的模型 ID
     */
    public void recordSessionConversation(ConversationSession session, AnalysisRequest request, String result,
            String skillUsed, String modelId) {
        persistConversation(session, request, result, skillUsed, modelId);
        captureConversationMemory(session, request, result);
    }

    /**
     * 记录完整的分析会话，并同步写入向量记忆。
     *
     * <p>该入口用于默认编排链和配置化 Agent 路由，确保分析结果不仅落到会话历史，
     * 还会进入后续可检索的长期记忆。</p>
     *
     * @param session 会话对象
     * @param request 原始请求
     * @param result 最终回答
     * @param skillUsed 使用的技能或 Agent 名称
     * @param modelId 选中的模型 ID
     */
    public void recordAnalysisConversation(ConversationSession session, AnalysisRequest request, String result,
            String skillUsed, String modelId) {
        persistConversation(session, request, result, skillUsed, modelId);
        captureConversationMemory(session, request, result);
        indexConversation(session, request, result);
    }

    private void persistConversation(ConversationSession session, AnalysisRequest request, String result,
            String skillUsed, String modelId) {
        if (session == null) {
            return;
        }
        session.addUserMessage(request.getQuestion());
        session.addAssistantMessage(result);
        try {
            sessionManager.saveMessage(session.getSessionId(), USER_ROLE, request.getQuestion(), null, null,
                    tokenMonitor.estimateTokens(request.getQuestion()));
            sessionManager.saveMessage(session.getSessionId(), ASSISTANT_ROLE, result, skillUsed, modelId,
                    tokenMonitor.estimateTokens(result));
        } catch (Exception e) {
            LOGGER.warn("持久化 Agent 对话失败，session: {}", session.getSessionId(), e);
        }
    }

    private void indexConversation(ConversationSession session, AnalysisRequest request, String result) {
        try {
            vectorMemoryService.indexConversation(
                    resolveSessionId(session),
                    request.getQuestion(),
                    preview(result, ASSISTANT_MEMORY_PREVIEW_LENGTH),
                    securityContextHelper.getCurrentTenantId(),
                    securityContextHelper.getCurrentUserId());
        } catch (Exception e) {
            LOGGER.warn("索引 ReAct 对话到向量记忆失败", e);
        }
    }

    private void captureConversationMemory(ConversationSession session, AnalysisRequest request, String result) {
        try {
            conversationMemoryCaptureService.captureCompletedConversation(
                    resolveSessionId(session),
                    request.getQuestion(),
                    result);
        } catch (Exception e) {
            LOGGER.warn("捕获对话记忆失败", e);
        }
    }

    private String resolveSessionId(ConversationSession session) {
        return session != null ? session.getSessionId() : ANONYMOUS_SESSION_ID;
    }

    private String preview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }
}
