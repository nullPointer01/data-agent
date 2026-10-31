package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import com.ai.repository.ConversationSessionRepository;
import com.ai.repository.ConversationMessageRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一执行语义记忆校验、幂等写入和画像刷新。
 *
 * @author data-agent
 */
@Service
public class SemanticMemoryService {

    private static final int SEMANTIC_IMPORTANCE = 5;
    private static final String EXTRACTOR_VERSION = "semantic-v2";
    private static final Set<MemoryType> ALLOWED_TYPES = Set.of(
            MemoryType.PREFERENCE, MemoryType.ENTITY, MemoryType.CONCLUSION);

    private final LongTermMemory longTermMemory;
    private final UserProfileMemoryRefreshService profileRefreshService;
    private final MemoryWriteGuard writeGuard;
    private final MemoryExtractionTaskMapper tasks;
    private final ConversationSessionRepository sessions;
    private final ConversationMessageRepository messages;

    public SemanticMemoryService(LongTermMemory longTermMemory,
            UserProfileMemoryRefreshService profileRefreshService, MemoryWriteGuard writeGuard,
            MemoryExtractionTaskMapper tasks, ConversationSessionRepository sessions,
            ConversationMessageRepository messages) {
        this.longTermMemory = longTermMemory;
        this.profileRefreshService = profileRefreshService;
        this.writeGuard = writeGuard;
        this.tasks = tasks;
        this.sessions = sessions;
        this.messages = messages;
    }

    /**
     * 幂等写入一批语义记忆，并在完成后统一重建画像。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param sessionId 来源会话 ID
     * @param candidates 语义候选
     * @return 实际处理数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int upsertAll(String tenantId, String userId, String sessionId,
            List<SemanticMemoryCandidate> candidates) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId) || candidates == null) {
            return 0;
        }
        if (deduplicate(candidates).isEmpty()) return 0;
        writeGuard.advance(tenantId, userId);
        return writeCandidates(tenantId, userId, sessionId, candidates);
    }

    /** 显式提取期间发生用户删除或纠错时，陈旧保存结果也不能覆盖最新操作。 */
    @Transactional(rollbackFor = Exception.class)
    public int upsertAllIfRevision(String tenantId, String userId, String sessionId,
            List<SemanticMemoryCandidate> candidates, long expectedRevision) {
        if (StringUtils.hasText(sessionId) && sessions.findOwnedForUpdate(sessionId, userId, tenantId)
                .filter(session -> "ACTIVE".equals(session.getStatus())).isEmpty()) {
            throw new IllegalStateException("来源会话已不可用，记忆未保存");
        }
        if (writeGuard.lock(tenantId, userId).revision() != expectedRevision) {
            throw new IllegalStateException("记忆已发生变更，请按最新情况重新提交保存");
        }
        return upsertAll(tenantId, userId, sessionId, candidates);
    }

    /** 模型调用结束后才进入短事务，失效来源、旧修订和乱序任务不能写回。 */
    @Transactional(rollbackFor = Exception.class)
    public int applyBackground(MemoryExtractionTask task, List<SemanticMemoryCandidate> candidates) {
        // 与删除使用相同的锁顺序：会话 -> 提取任务 -> 用户记忆修订。
        if (sessions.findOwnedForUpdate(task.sessionId(), task.userId(), task.tenantId())
                .filter(session -> "ACTIVE".equals(session.getStatus()) || "ARCHIVED".equals(session.getStatus())).isEmpty()) {
            return 0;
        }
        if (tasks.lockOwned(task) == null) throw new IllegalStateException("记忆提取租约已失效");
        var state = writeGuard.lock(task.tenantId(), task.userId());
        if (state.revision() != task.revision() || state.lastSourceMessageId() >= task.sourceMessageId()
                || messages.findById(task.sourceMessageId())
                    .filter(message -> task.sessionId().equals(message.getSessionId()) && "user".equals(message.getRole())).isEmpty()) {
            return 0;
        }
        // 显式保存只能通过绑定的工具确认，不能借后台模型误判绕过能力授权。
        int count = writeCandidates(task.tenantId(), task.userId(), task.sessionId(), candidates.stream()
                .filter(candidate -> candidate != null && !candidate.explicit()).toList());
        writeGuard.processed(task.tenantId(), task.userId(), task.sourceMessageId());
        return count;
    }

    private int writeCandidates(String tenantId, String userId, String sessionId,
            List<SemanticMemoryCandidate> candidates) {
        int affected = 0;
        for (SemanticMemoryCandidate candidate : deduplicate(candidates)) {
            if (!isValid(candidate)) {
                continue;
            }
            MemorySource source = candidate.explicit() ? MemorySource.USER_EXPLICIT : MemorySource.USER_IMPLICIT;
            MemoryCaptureRequest request = new MemoryCaptureRequest(
                    MemoryTier.LONG_TERM,
                    candidate.type(),
                    source,
                    sessionId,
                    candidate.evidence(),
                    candidate.content(),
                    Map.of(
                            "extractorVersion", EXTRACTOR_VERSION,
                            "evidence", candidate.evidence(),
                            "explicit", candidate.explicit()),
                    candidate.semanticKey(),
                    candidate.confidence(),
                    List.of(),
                    List.of("semantic_memory"),
                    SEMANTIC_IMPORTANCE);
            longTermMemory.upsertSemantic(tenantId, userId, request);
            affected++;
        }
        if (affected > 0) {
            profileRefreshService.refresh(tenantId, userId);
        }
        return affected;
    }

    private boolean isValid(SemanticMemoryCandidate candidate) {
        return candidate != null
                && ALLOWED_TYPES.contains(candidate.type())
                && SemanticMemoryKey.isValid(candidate.semanticKey())
                && StringUtils.hasText(candidate.content())
                && candidate.confidence() >= 0D
                && candidate.confidence() <= 1D;
    }

    private List<SemanticMemoryCandidate> deduplicate(List<SemanticMemoryCandidate> candidates) {
        Map<String, SemanticMemoryCandidate> unique = new LinkedHashMap<>();
        for (SemanticMemoryCandidate candidate : candidates) {
            if (isValid(candidate)) {
                unique.put(candidate.type().name() + ":" + candidate.semanticKey(), candidate);
            }
        }
        return List.copyOf(unique.values());
    }
}
