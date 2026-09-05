package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import com.ai.memory.dto.MemoryEntrySummary;
import com.ai.repository.MemoryEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 基于 MySQL 的短期记忆存储。
 *
 * @author data-agent
 */
@Service
public class ShortTermMemory {

    private static final int DEFAULT_LIMIT = 5;

    private final MemoryEntryRepository memoryEntryRepository;
    private final MemoryEntryFactory memoryEntryFactory;
    private final MemoryProperties memoryProperties;

    public ShortTermMemory(MemoryEntryRepository memoryEntryRepository,
            MemoryEntryFactory memoryEntryFactory,
            MemoryProperties memoryProperties) {
        this.memoryEntryRepository = memoryEntryRepository;
        this.memoryEntryFactory = memoryEntryFactory;
        this.memoryProperties = memoryProperties;
    }

    /**
     * 为租户用户写入短期记忆。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param request 写入请求
     * @return 持久化后的记忆
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryEntry capture(String tenantId, String userId, MemoryCaptureRequest request) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("tenantId and userId are required");
        }
        MemoryEntry entry = memoryEntryFactory.create(tenantId, userId, request);
        entry.setTier(MemoryTier.SHORT_TERM);
        return memoryEntryRepository.save(entry);
    }

    /**
     * 检索近期可用短期记忆。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param limit 最大返回数量
     * @return 记忆摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public List<MemoryEntrySummary> recall(String tenantId, String userId, int limit) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
            return List.of();
        }
        int normalizedLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        List<MemoryEntry> entries = memoryEntryRepository.findActiveByTenantUserAndTier(
                        tenantId, userId, MemoryTier.SHORT_TERM, LocalDateTime.now(),
                        PageRequest.of(0, normalizedLimit));
        markAccessed(entries);
        return entries.stream().map(MemoryEntrySummary::from).toList();
    }

    /**
     * 对短期记忆应用时间衰减。
     *
     * @param now 当前时间
     * @param limit 最大处理数量
     * @return 衰减维护结果
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryDecayResult applyDecay(LocalDateTime now, int limit) {
        List<MemoryEntry> entries = memoryEntryRepository.findByTierOrderByUpdatedAtAsc(
                MemoryTier.SHORT_TERM, PageRequest.of(0, normalizeLimit(limit)));
        int deletedCount = 0;
        int updatedCount = 0;
        for (MemoryEntry entry : entries) {
            double newWeight = calculateShortTermWeight(entry, now);
            if (newWeight < memoryProperties.getShortTermDeleteThreshold()) {
                memoryEntryRepository.delete(entry);
                deletedCount++;
                continue;
            }
            entry.setDecayWeight(newWeight);
            memoryEntryRepository.save(entry);
            updatedCount++;
        }
        return new MemoryDecayResult(updatedCount, deletedCount);
    }

    /**
     * 查找可以晋升为长期记忆的高价值短期记忆。
     *
     * @param now 当前时间
     * @param limit 最大返回数量
     * @return 晋升候选
     */
    @Transactional(readOnly = true)
    public List<MemoryEntry> findPromotionCandidates(LocalDateTime now, int limit) {
        return memoryEntryRepository.findPromotionCandidates(
                memoryProperties.getPromotionDecayWeightThreshold(),
                memoryProperties.getPromotionAccessCountThreshold(),
                now,
                PageRequest.of(0, normalizeLimit(limit)));
    }

    private void markAccessed(List<MemoryEntry> entries) {
        LocalDateTime now = LocalDateTime.now();
        entries.forEach(entry -> {
            entry.setAccessCount(entry.getAccessCount() + 1);
            entry.setLastAccessedAt(now);
        });
        memoryEntryRepository.saveAll(entries);
    }

    private double calculateShortTermWeight(MemoryEntry entry, LocalDateTime now) {
        LocalDateTime createdAt = entry.getCreatedAt() == null ? now : entry.getCreatedAt();
        long daysSinceCreated = Math.max(0L, ChronoUnit.DAYS.between(createdAt, now));
        double timeDecay = Math.pow(memoryProperties.getShortTermDecayRate(), daysSinceCreated);
        double accessBoost = Math.min(2D, 1D + entry.getAccessCount() * 0.1D);
        double baseImportance = normalizeWeight(entry.getRelevanceScore());
        return Math.min(1D, roundWeight(timeDecay * accessBoost * baseImportance));
    }

    private double normalizeWeight(double value) {
        return value > 0D ? Math.min(1D, value) : 0.6D;
    }

    private double roundWeight(double value) {
        return Math.round(value * 10000D) / 10000D;
    }

    private int normalizeLimit(int limit) {
        int fallback = Math.max(1, memoryProperties.getRetentionBatchSize());
        return limit > 0 ? limit : fallback;
    }
}
