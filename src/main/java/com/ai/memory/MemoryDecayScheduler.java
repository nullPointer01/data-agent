package com.ai.memory;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆衰减、晋升和过期清理调度器。
 *
 * @author data-agent
 */
@Service
public class MemoryDecayScheduler {

    private static final String METRIC_MAINTENANCE_TOTAL = "data_agent_memory_maintenance_total";

    private final ShortTermMemory shortTermMemory;
    private final LongTermMemory longTermMemory;
    private final MemoryRetentionService memoryRetentionService;
    private final MemoryProperties memoryProperties;
    private final MeterRegistry meterRegistry;
    private final UserProfileMemoryRefreshService userProfileMemoryRefreshService;

    public MemoryDecayScheduler(ShortTermMemory shortTermMemory,
            LongTermMemory longTermMemory,
            MemoryRetentionService memoryRetentionService,
            MemoryProperties memoryProperties,
            MeterRegistry meterRegistry,
            UserProfileMemoryRefreshService userProfileMemoryRefreshService) {
        this.shortTermMemory = shortTermMemory;
        this.longTermMemory = longTermMemory;
        this.memoryRetentionService = memoryRetentionService;
        this.memoryProperties = memoryProperties;
        this.meterRegistry = meterRegistry;
        this.userProfileMemoryRefreshService = userProfileMemoryRefreshService;
    }

    /**
     * 定时维护记忆权重，并把高价值短期记忆晋升为长期记忆。
     */
    @Scheduled(cron = "${app.memory.decay-cron:0 0 3 * * *}")
    @Transactional(rollbackFor = Exception.class)
    public void maintainMemories() {
        maintainMemories(LocalDateTime.now(), memoryProperties.getRetentionBatchSize());
    }

    /**
     * 执行一次记忆维护。
     *
     * @param now 当前时间
     * @param limit 最大处理数量
     * @return 维护结果
     */
    public MemoryMaintenanceResult maintainMemories(LocalDateTime now, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        MemoryDecayResult shortTermResult = shortTermMemory.applyDecay(now, normalizedLimit);
        MemoryDecayResult longTermResult = longTermMemory.applyDecay(now, normalizedLimit);
        int promotedCount = promoteHighValueMemories(now, normalizedLimit);
        int expiredDeletedCount = memoryRetentionService.cleanupExpiredMemories(now, normalizedLimit);
        recordMetrics(shortTermResult, longTermResult, promotedCount, expiredDeletedCount);
        return new MemoryMaintenanceResult(shortTermResult.updatedCount(), shortTermResult.deletedCount(),
                longTermResult.updatedCount(), promotedCount, expiredDeletedCount);
    }

    private int promoteHighValueMemories(LocalDateTime now, int limit) {
        List<MemoryEntry> candidates = shortTermMemory.findPromotionCandidates(now, limit);
        candidates.forEach(this::promoteMemory);
        return candidates.size();
    }

    private void promoteMemory(MemoryEntry candidate) {
        MemoryEntry promoted = longTermMemory.savePromoted(candidate);
        userProfileMemoryRefreshService.refresh(promoted.getTenantId(), promoted.getUserId());
    }

    private void recordMetrics(MemoryDecayResult shortTermResult, MemoryDecayResult longTermResult,
            int promotedCount, int expiredDeletedCount) {
        meterRegistry.counter(METRIC_MAINTENANCE_TOTAL, "action", "short_term_updated")
                .increment(shortTermResult.updatedCount());
        meterRegistry.counter(METRIC_MAINTENANCE_TOTAL, "action", "short_term_deleted")
                .increment(shortTermResult.deletedCount());
        meterRegistry.counter(METRIC_MAINTENANCE_TOTAL, "action", "long_term_updated")
                .increment(longTermResult.updatedCount());
        meterRegistry.counter(METRIC_MAINTENANCE_TOTAL, "action", "promoted").increment(promotedCount);
        meterRegistry.counter(METRIC_MAINTENANCE_TOTAL, "action", "expired_deleted")
                .increment(expiredDeletedCount);
    }

    private int normalizeLimit(int limit) {
        int fallback = Math.max(1, memoryProperties.getRetentionBatchSize());
        return limit > 0 ? limit : fallback;
    }
}
