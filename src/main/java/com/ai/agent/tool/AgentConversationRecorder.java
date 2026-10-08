package com.ai.agent.tool;

import com.ai.agent.durable.AgentDurableRunStore;
import com.ai.mcp.TokenMonitor;
import com.ai.memory.ConversationMemoryCaptureService;
import com.ai.model.AnalysisRequest;
import com.ai.model.ConversationSession;
import com.ai.security.SecurityContextHelper;
import com.ai.service.SessionManager;
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
    private static final String ANONYMOUS_SESSION_ID = "anonymous";
    private static final String REACT_SKILL_NAME = "react-agent";
    private static final String WAITING_APPROVAL_MESSAGE = "工具操作已提交审批，审批通过后会继续执行。";

    private final TokenMonitor tokenMonitor;
    private final AgentDurableRunStore durableRunStore;
    private final SessionManager sessionManager;
    private final SecurityContextHelper securityContextHelper;
    private final ConversationMemoryCaptureService conversationMemoryCaptureService;

    public AgentConversationRecorder(TokenMonitor tokenMonitor,
            AgentDurableRunStore durableRunStore,
            SessionManager sessionManager,
            SecurityContextHelper securityContextHelper,
            ConversationMemoryCaptureService conversationMemoryCaptureService) {
        this.tokenMonitor = tokenMonitor;
        this.durableRunStore = durableRunStore;
        this.sessionManager = sessionManager;
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
     * @param runId 本次 Agent Run 编号
     */
    public void recordReActConversation(ConversationSession session, AnalysisRequest request, String result,
            String modelId, String runId) {
        if (!persistConversation(session, request, result, REACT_SKILL_NAME, modelId, runId, true)) {
            return;
        }
        captureConversationMemory(session, request, result, modelId);
    }

    /**
     * 记录会话范围内的命令或技能对话，不进行原始对话的向量索引。
     *
     * @param session 会话对象
     * @param request 原始请求
     * @param result 最终回答
     * @param skillUsed 使用的技能或命令名称
     * @param modelId 选中的模型 ID
     * @param runId 本次 Agent Run 编号
     */
    public void recordSessionConversation(ConversationSession session, AnalysisRequest request, String result,
            String skillUsed, String modelId, String runId) {
        if (persistConversation(session, request, result, skillUsed, modelId, runId, false)) {
            captureConversationMemory(session, request, result, modelId);
        }
    }

    /**
     * 记录完整的分析会话，并同事务提交后台向量索引任务。
     *
     * <p>该入口用于默认编排链和配置化 Agent 路由，确保分析结果不仅落到会话历史，
     * 还会进入后续可检索的长期记忆。</p>
     *
     * @param session 会话对象
     * @param request 原始请求
     * @param result 最终回答
     * @param skillUsed 使用的技能或 Agent 名称
     * @param modelId 选中的模型 ID
     * @param runId 本次 Agent Run 编号
     */
    public void recordAnalysisConversation(ConversationSession session, AnalysisRequest request, String result,
            String skillUsed, String modelId, String runId) {
        if (!persistConversation(session, request, result, skillUsed, modelId, runId, true)) {
            return;
        }
        captureConversationMemory(session, request, result, modelId);
    }

    /**
     * 保存进入等待审批状态的对话，使其可在重新登录后恢复展示。
     *
     * @param session 会话对象
     * @param request 原始请求
     * @param skillUsed 使用的技能或 Agent 名称
     * @param modelId 选中的模型 ID
     * @param runId 本次 Agent Run 编号
     */
    public void recordWaitingApprovalConversation(ConversationSession session,
            AnalysisRequest request,
            String skillUsed,
            String modelId,
            String runId) {
        persistConversation(session, request, WAITING_APPROVAL_MESSAGE, skillUsed, modelId, runId, false);
    }

    /**
     * 将等待审批消息更新为 Run 的最新用户可见状态。
     *
     * @param runId Agent Run 编号
     * @param content 用户可见内容
     * @param skillUsed 使用的技能或 Agent 名称
     * @param modelId 选中的模型 ID
     */
    public void updateRunConversation(String runId,
            String content,
            String skillUsed,
            String modelId) {
        try {
            durableRunStore.find(runId).ifPresent(run -> sessionManager.updateRunAssistantMessage(
                    run.getSessionId(),
                    run.getTenantId(),
                    run.getUserId(),
                    run.getRunId(),
                    content,
                    skillUsed,
                    modelId,
                    tokenMonitor.estimateTokens(content, modelId), null));
        } catch (Exception e) {
            LOGGER.warn("更新 Agent Run 会话状态失败，run: {}", runId, e);
        }
    }

    /** 恢复成功回答必须先保存；主库和索引任务失败时让恢复流程进入失败状态。 */
    public void recordResumedConversation(String runId, String question, String answer, String modelId) {
        var run = durableRunStore.find(runId).orElseThrow(() -> new IllegalStateException("待恢复 Run 不存在"));
        if (!sessionManager.updateRunAssistantMessage(run.getSessionId(), run.getTenantId(), run.getUserId(),
                runId, answer, null, modelId, tokenMonitor.estimateTokens(answer, modelId), question)) {
            throw new IllegalStateException("恢复回答生成成功，但会话保存失败");
        }
    }

    private boolean persistConversation(ConversationSession session, AnalysisRequest request, String result,
            String skillUsed, String modelId, String runId, boolean indexConversation) {
        if (session == null) {
            throw new IllegalStateException("会话缺失，回答未保存");
        }
        try {
            boolean saved = sessionManager.saveConversationTurn(
                    session.getSessionId(),
                    request.getQuestion(),
                    result,
                    skillUsed,
                    modelId,
                    tokenMonitor.estimateTokens(request.getQuestion(), modelId),
                    tokenMonitor.estimateTokens(result, modelId),
                    runId, indexConversation);
            if (saved) {
                session.addUserMessage(request.getQuestion());
                session.addAssistantMessage(result);
            }
            return saved;
        } catch (Exception e) {
            LOGGER.warn("持久化 Agent 对话失败，session: {}", session.getSessionId(), e);
            throw new IllegalStateException("回答生成成功，但会话保存失败，请稍后重试", e);
        }
    }

    private void captureConversationMemory(
            ConversationSession session, AnalysisRequest request, String result, String modelId) {
        try {
            conversationMemoryCaptureService.captureCompletedConversation(
                    resolveSessionId(session), request.getQuestion(), result, modelId);
        } catch (Exception e) {
            LOGGER.warn("捕获对话记忆失败", e);
        }
    }

    private String resolveSessionId(ConversationSession session) {
        return session != null ? session.getSessionId() : ANONYMOUS_SESSION_ID;
    }

}
