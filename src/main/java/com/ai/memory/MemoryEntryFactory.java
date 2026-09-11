package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 从记忆写入请求构建规范化的持久化实体。
 *
 * @author data-agent
 */
@Component
public class MemoryEntryFactory {

    private static final double MAX_IMPORTANCE = 5D;

    private final MemoryJsonCodec memoryJsonCodec;
    private final MemoryProperties memoryProperties;

    public MemoryEntryFactory(MemoryJsonCodec memoryJsonCodec, MemoryProperties memoryProperties) {
        this.memoryJsonCodec = memoryJsonCodec;
        this.memoryProperties = memoryProperties;
    }

    /**
     * 为租户用户创建记忆实体。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param request 写入请求
     * @return 记忆实体
     */
    public MemoryEntry create(String tenantId, String userId, MemoryCaptureRequest request) {
        MemoryCaptureRequest normalized = request.normalize();
        MemoryEntry entry = new MemoryEntry();
        entry.setTenantId(tenantId);
        entry.setUserId(userId);
        entry.setSessionId(normalized.sessionId());
        entry.setTier(normalized.tier());
        entry.setType(normalized.type());
        entry.setSource(normalized.source());
        entry.setContent(resolveStoredOriginalContent(normalized));
        entry.setCompressedContent(normalized.effectiveContent());
        entry.setSourceContentLength(textLength(normalized.content()));
        entry.setStoredContentLength(textLength(normalized.effectiveContent()));
        entry.setMetadataJson(memoryJsonCodec.toJson(normalized.metadata()));
        entry.setSemanticKey(normalized.semanticKey());
        entry.setConfidence(normalized.confidence());
        entry.setKeyEntitiesJson(memoryJsonCodec.toJson(normalized.keyEntities()));
        entry.setTopicTagsJson(memoryJsonCodec.toJson(normalized.topicTags()));
        entry.setRelevanceScore(scoreImportance(normalized.importance()));
        entry.setDecayWeight(scoreImportance(normalized.importance()));
        entry.setExpiresAt(resolveExpiresAt(normalized.tier()));
        return entry;
    }

    private String resolveStoredOriginalContent(MemoryCaptureRequest request) {
        if (MemoryTier.LONG_TERM.equals(request.tier())) {
            return request.content();
        }
        return null;
    }

    private long textLength(String value) {
        return StringUtils.hasText(value) ? value.length() : 0L;
    }

    private LocalDateTime resolveExpiresAt(MemoryTier tier) {
        if (MemoryTier.SHORT_TERM.equals(tier)) {
            return LocalDateTime.now().plusDays(normalizeRetentionDays());
        }
        return null;
    }

    private int normalizeRetentionDays() {
        return Math.max(1, memoryProperties.getShortTermRetentionDays());
    }

    private double scoreImportance(int importance) {
        int bounded = Math.max(1, Math.min((int) MAX_IMPORTANCE, importance));
        return bounded / MAX_IMPORTANCE;
    }
}
