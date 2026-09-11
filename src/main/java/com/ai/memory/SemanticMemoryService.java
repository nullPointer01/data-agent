package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private static final String EXTRACTOR_VERSION = "semantic-v1";
    private static final Set<MemoryType> ALLOWED_TYPES = Set.of(
            MemoryType.PREFERENCE, MemoryType.ENTITY, MemoryType.CONCLUSION);

    private final LongTermMemory longTermMemory;
    private final UserProfileMemoryRefreshService profileRefreshService;

    public SemanticMemoryService(LongTermMemory longTermMemory,
            UserProfileMemoryRefreshService profileRefreshService) {
        this.longTermMemory = longTermMemory;
        this.profileRefreshService = profileRefreshService;
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
    public int upsertAll(String tenantId, String userId, String sessionId,
            List<SemanticMemoryCandidate> candidates) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId) || candidates == null) {
            return 0;
        }
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
