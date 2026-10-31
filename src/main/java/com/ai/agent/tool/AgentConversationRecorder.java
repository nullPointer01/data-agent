package com.ai.agent.tool;

import com.ai.agent.durable.AgentDurableRunStore;
import com.ai.mcp.TokenMonitor;
import com.ai.memory.MemoryCaptureMode;
import com.ai.model.AnalysisRequest;
import com.ai.model.ConversationSession;
import com.ai.service.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 持久化 Agent 对话记录，并按请求计划调度记忆捕获。
 *
 * @author data-agent
 */
@Component
public class AgentConversationRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentConversationRecorder.class);
    private static final String WAITING_APPROVAL_MESSAGE = "工具操作已提交审批，审批通过后会继续执行。";

    private final TokenMonitor tokenMonitor;
    private final AgentDurableRunStore durableRunStore;
    private final SessionManager sessionManager;

    public AgentConversationRecorder(TokenMonitor tokenMonitor,
            AgentDurableRunStore durableRunStore,
            SessionManager sessionManager) {
        this.tokenMonitor = tokenMonitor;
        this.durableRunStore = durableRunStore;
        this.sessionManager = sessionManager;
    }

    /**
     * 记录完整的分析会话，并同事务提交精选记忆提取任务。
     *
     * <p>完整问答只保存于主库；长期记忆由请求计划调度、提取器准入。</p>
     *
     * @param session 会话对象
     * @param request 原始请求
     * @param result 最终回答
     * @param skillUsed 使用的技能或 Agent 名称
     * @param modelId 选中的模型 ID
     * @param runId 本次 Agent Run 编号
     * @param captureMode 本轮长期记忆提取调度
     * @param memoryRevision 请求开始时的记忆修订快照
     */
    public void recordAnalysisConversation(ConversationSession session, AnalysisRequest request, String result,
            String skillUsed, String modelId, String runId, MemoryCaptureMode captureMode, long memoryRevision) {
        persistConversation(session, request, result, skillUsed, modelId, runId, captureMode, memoryRevision);
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
        persistConversation(session, request, WAITING_APPROVAL_MESSAGE, skillUsed, modelId, runId,
                MemoryCaptureMode.SKIP, 0L);
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
                    tokenMonitor.estimateTokens(content, modelId)));
        } catch (Exception e) {
            LOGGER.warn("更新 Agent Run 会话状态失败，run: {}", runId, e);
        }
    }

    /** 恢复成功回答必须先保存，主库失败时让恢复流程进入失败状态。 */
    public void recordResumedConversation(String runId, String answer, String modelId) {
        var run = durableRunStore.find(runId).orElseThrow(() -> new IllegalStateException("待恢复 Run 不存在"));
        if (!sessionManager.updateRunAssistantMessage(run.getSessionId(), run.getTenantId(), run.getUserId(),
                runId, answer, null, modelId, tokenMonitor.estimateTokens(answer, modelId))) {
            throw new IllegalStateException("恢复回答生成成功，但会话保存失败");
        }
    }

    private boolean persistConversation(ConversationSession session, AnalysisRequest request, String result,
            String skillUsed, String modelId, String runId, MemoryCaptureMode captureMode, long memoryRevision) {
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
                    runId, captureMode, memoryRevision);
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

}
