package com.ai.conversation;

import com.ai.agent.runtime.AgentRunRegistry;
import com.ai.agent.approval.ApprovalChanged;
import com.ai.agent.approval.ApprovalEventPublisher;
import com.ai.conversation.dto.SessionListResponse;
import com.ai.memory.WorkingMemory;
import com.ai.repository.ConversationSessionRepository;
import com.ai.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;

/** 归档只整理列表；删除撤销后续执行，并在保留期后清理会话内容。 */
@Service
public class ConversationLifecycleService {
    private static final Logger LOG = LoggerFactory.getLogger(ConversationLifecycleService.class);
    private final ConversationLifecycleMapper mapper;
    private final ConversationSessionRepository sessions;
    private final SecurityContextHelper identity;
    private final AgentRunRegistry registry;
    private final StringRedisTemplate redis;
    private final WorkingMemory workingMemory;
    private final ApprovalEventPublisher approvalEvents;

    public ConversationLifecycleService(ConversationLifecycleMapper mapper, ConversationSessionRepository sessions,
            SecurityContextHelper identity, AgentRunRegistry registry, StringRedisTemplate redis, WorkingMemory workingMemory,
            ApprovalEventPublisher approvalEvents) {
        this.mapper = mapper;
        this.sessions = sessions;
        this.identity = identity;
        this.registry = registry;
        this.redis = redis;
        this.workingMemory = workingMemory;
        this.approvalEvents = approvalEvents;
    }

    public SessionListResponse list(String status, String cursor, int limit) {
        if (!"ACTIVE".equals(status) && !"ARCHIVED".equals(status)) {
            throw new IllegalArgumentException("会话状态无效");
        }
        int size = Math.max(1, Math.min(50, limit));
        var rows = new ArrayList<>(mapper.list(identity.getCurrentTenantId(), identity.getCurrentUserId(), status,
                ConversationCursor.decode(cursor), size + 1));
        boolean more = rows.size() > size;
        if (more) rows.remove(rows.size() - 1);
        var last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        String next = more ? new ConversationCursor(last.getLastAccessAt(), last.getSessionId()).encode() : null;
        return new SessionListResponse(true, rows, next);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeState(String sessionId, String target) {
        if (!"ACTIVE".equals(target) && !"ARCHIVED".equals(target) && !"DELETED".equals(target)) {
            throw new IllegalArgumentException("会话状态无效");
        }
        var session = sessions.findOwnedForUpdate(sessionId, identity.getCurrentUserId(), identity.getCurrentTenantId())
                .orElseThrow(() -> new IllegalArgumentException("会话不存在或无权限"));
        if ("DELETED".equals(session.getStatus()) || "CLOSED".equals(session.getStatus())) {
            if ("DELETED".equals(target)) return; // 重复删除幂等，不延长保留期限。
            throw new IllegalArgumentException("会话已删除，不能恢复或归档");
        }
        if (target.equals(session.getStatus())) return;
        if (!"DELETED".equals(target) && (mapper.hasPendingRun(sessionId)
                || registry.hasSession(sessionId, session.getTenantId(), session.getUserId()))) {
            throw new IllegalArgumentException("会话仍有运行或待审批任务，请先停止或取消后再归档");
        }
        mapper.changeState(sessionId, target);
        if ("DELETED".equals(target)) {
            mapper.cancelApprovals(sessionId);
            mapper.cancelRuns(sessionId);
            mapper.cancelExtraction(sessionId);
            approvalEvents.changed(new ApprovalChanged(session.getTenantId(), session.getUserId(), sessionId, null, null));
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                if ("DELETED".equals(target)) registry.cancelSession(sessionId, session.getTenantId(), session.getUserId());
                try {
                    redis.delete("session:" + sessionId);
                    workingMemory.clear(session.getTenantId(), session.getUserId(), sessionId);
                } catch (RuntimeException error) {
                    LOG.warn("会话状态已提交，缓存失效失败: sessionId={}", sessionId);
                }
            }
        });
    }

    /** 每个会话独立短事务；多实例使用同一行锁，锁定后再次检查保留期限。 */
    @Transactional(rollbackFor = Exception.class)
    public void purgeOne(String sessionId, int retentionDays, int batchSize) {
        // 删除时间与到期判断都使用数据库时钟，避免 Docker/应用时区不同导致提前清理。
        if (mapper.lockExpired(sessionId, retentionDays) == null) return;
        mapper.purgeMessages(sessionId, batchSize);
        mapper.purgeTasks(sessionId, batchSize);
        mapper.purgeApprovals(sessionId, batchSize);
        mapper.purgeRunEvents(sessionId, batchSize);
        mapper.purgeRunSubmissions(sessionId, batchSize);
        mapper.purgeRuns(sessionId, batchSize);
        mapper.purgeTraces(sessionId, batchSize);
        mapper.purgeSummaries(sessionId, batchSize);
        mapper.purgeSessionIfEmpty(sessionId);
    }
}
