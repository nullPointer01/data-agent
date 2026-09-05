package com.ai.memory;

import com.ai.repository.MemoryEntryRepository;
import com.ai.service.VectorMemoryService;
import com.ai.vector.VectorDocumentTypes;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户记忆配额控制服务。
 *
 * @author data-agent
 */
@Service
public class MemoryQuotaService {

    private static final String METRIC_CLEANUP_TOTAL = "data_agent_memory_cleanup_total";

    private final MemoryEntryRepository memoryEntryRepository;
    private final VectorMemoryService vectorMemoryService;
    private final MemoryProperties memoryProperties;
    private final MeterRegistry meterRegistry;

    public MemoryQuotaService(MemoryEntryRepository memoryEntryRepository,
            VectorMemoryService vectorMemoryService,
            MemoryProperties memoryProperties,
            MeterRegistry meterRegistry) {
        this.memoryEntryRepository = memoryEntryRepository;
        this.vectorMemoryService = vectorMemoryService;
        this.memoryProperties = memoryProperties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 写入新记忆前裁剪低价值旧记忆，保证用户总量不超过配额。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 被裁剪的记忆数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int pruneBeforeCapture(String tenantId, String userId) {
        int quota = Math.max(1, memoryProperties.getMaxMemoriesPerUser());
        long currentCount = memoryEntryRepository.countByTenantIdAndUserId(tenantId, userId);
        if (currentCount < quota) {
            return 0;
        }

        int pruneCount = Math.toIntExact(Math.min(currentCount - quota + 1,
                Math.max(1, memoryProperties.getRetentionBatchSize())));
        List<MemoryEntry> prunableEntries = memoryEntryRepository.findPrunableMemories(
                tenantId, userId, PageRequest.of(0, pruneCount));
        prunableEntries.forEach(this::cleanupVectorIfNecessary);
        memoryEntryRepository.deleteAll(prunableEntries);
        meterRegistry.counter(METRIC_CLEANUP_TOTAL, "reason", "quota").increment(prunableEntries.size());
        return prunableEntries.size();
    }

    private void cleanupVectorIfNecessary(MemoryEntry entry) {
        if (MemoryTier.LONG_TERM.equals(entry.getTier())) {
            vectorMemoryService.removeFromStore(VectorDocumentTypes.MEMORY, entry.getMemoryId(), entry.getTenantId());
        }
    }
}
