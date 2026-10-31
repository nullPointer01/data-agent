package com.ai.conversation;

import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.AgentRunRegistry;
import com.ai.model.ConversationSessionEntity;
import com.ai.repository.ConversationSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 会话状态统一准入：历史可读取归档会话，模型与工具只能使用有效会话。 */
@Component
public class ConversationAccessGuard {
    private final ConversationSessionRepository sessions;
    private final AgentRunRegistry registry;

    public ConversationAccessGuard(ConversationSessionRepository sessions, AgentRunRegistry registry) {
        this.sessions = sessions;
        this.registry = registry;
    }

    public ConversationSessionEntity requireReadable(String sessionId, String tenantId, String userId) {
        return sessions.findBySessionIdAndUserIdAndTenantId(sessionId, userId, tenantId)
                .filter(session -> "ACTIVE".equals(session.getStatus()) || "ARCHIVED".equals(session.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("会话不存在、已删除或无权限"));
    }

    public void requireActive(String sessionId, String tenantId, String userId) {
        if (!"ACTIVE".equals(requireReadable(sessionId, tenantId, userId).getStatus())) {
            throw new IllegalStateException("会话已归档，请先恢复再继续对话");
        }
    }

    /** 创建/挂起 Run 与会话删除使用相同的行锁，防止删除后出现新任务。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockActive(String sessionId, String tenantId, String userId) {
        if (!StringUtils.hasText(sessionId)) return;
        sessions.findOwnedForUpdate(sessionId, userId, tenantId)
                .filter(session -> "ACTIVE".equals(session.getStatus()))
                .orElseThrow(() -> new IllegalStateException("会话已删除、归档或无权限"));
    }

    /** 注册与状态变更串行；适用于关闭持久化 Run 的本地运行及审批恢复。 */
    @Transactional
    public void registerRun(AgentRunContext context) {
        lockActive(context.sessionId(), context.tenantId(), context.userId());
        registry.register(context);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) registry.remove(context);
            }
        });
    }

    /** 内容写回先锁定来源会话，删除/清理期间不再追加 Trace 等副本。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean allowContentWrite(String sessionId, String tenantId, String userId) {
        if (!StringUtils.hasText(sessionId)) return true;
        return sessions.findOwnedForUpdate(sessionId, userId, tenantId)
                .filter(session -> "ACTIVE".equals(session.getStatus()) || "ARCHIVED".equals(session.getStatus()))
                .isPresent();
    }

    /** 无会话的评测/内部任务不受会话生命周期影响；关联会话的请求必须重新检查。 */
    public void checkRun(AgentRunContext context) {
        if (StringUtils.hasText(context.sessionId())) {
            try {
                requireActive(context.sessionId(), context.tenantId(), context.userId());
            } catch (IllegalArgumentException | IllegalStateException failure) {
                context.control().cancel("所属会话已不可用");
                context.control().ensureActive();
            }
        }
    }

    public void checkCurrentRun() {
        AgentRunScope.current().ifPresent(this::checkRun);
    }
}
