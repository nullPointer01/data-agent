package com.ai.memory;

import com.ai.repository.MemoryEntryRepository;
import com.ai.service.VectorMemoryService;
import com.ai.vector.VectorDocumentTypes;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆保留和过期清理服务。
 *
 * @author data-agent
 */
@Service
public class MemoryRetentionService {

    private static final String METRIC_CLEANUP_TOTAL = "data_agent_memory_cleanup_total";

    private final MemoryEntryRepository memoryEntryRepository;
    private final VectorMemoryService vectorMemoryService;
    private final MemoryProperties memoryProperties;
    private final MeterRegistry meterRegistry;

    public MemoryRetentionService(MemoryEntryRepository memoryEntryRepository,
            VectorMemoryService vectorMemoryService,
            MemoryProperties memoryProperties,
            MeterRegistry meterRegistry) {
        this.memoryEntryRepository = memoryEntryRepository;
        this.vectorMemoryService = vectorMemoryService;
        this.memoryProperties = memoryProperties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 清理当前时间前已经过期的记忆。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredMemories() {
        cleanupExpiredMemories(LocalDateTime.now(), memoryProperties.getRetentionBatchSize());
    }

    /**
     * 清理指定时间前已经过期的记忆。
     *
     * @param now 当前时间
     * @param limit 最大清理数量
     * @return 清理数量
     */
    public int cleanupExpiredMemories(LocalDateTime now, int limit) {
        List<MemoryEntry> expired = memoryEntryRepository.findExpiredMemories(now,
                PageRequest.of(0, normalizeLimit(limit)));
        if (expired.isEmpty()) {
            return 0;
        }
        expired.forEach(this::cleanupVectorIfNecessary);
        memoryEntryRepository.deleteAll(expired);
        meterRegistry.counter(METRIC_CLEANUP_TOTAL, "reason", "expired").increment(expired.size());
        return expired.size();
    }

    private void cleanupVectorIfNecessary(MemoryEntry entry) {
        if (MemoryTier.LONG_TERM.equals(entry.getTier())) {
            vectorMemoryService.removeFromStore(VectorDocumentTypes.MEMORY, entry.getMemoryId(), entry.getTenantId());
        }
    }

    private int normalizeLimit(int limit) {
        int fallback = Math.max(1, memoryProperties.getRetentionBatchSize());
        return limit > 0 ? limit : fallback;
    }
}
