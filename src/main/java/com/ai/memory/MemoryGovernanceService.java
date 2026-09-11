package com.ai.memory;

import com.ai.memory.dto.MemoryEntryResponse;
import com.ai.memory.dto.MemoryListResponse;
import com.ai.memory.dto.MemoryMutationResponse;
import com.ai.memory.dto.MemoryStatsResponse;
import com.ai.memory.dto.SemanticMemoryUpdateRequest;
import com.ai.memory.dto.UserMemoryProfileResponse;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.repository.MemoryEntryRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.service.AuditLogService;
import com.ai.service.VectorMemoryService;
import com.ai.vector.VectorDocumentTypes;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 当前用户记忆治理服务。
 *
 * @author data-agent
 */
@Service
public class MemoryGovernanceService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 200;
    private static final int PROFILE_LIMIT = 20;
    private static final String AUDIT_RESOURCE_MEMORY = "memory";
    private static final String AUDIT_STATUS_SUCCESS = "SUCCESS";
    private static final String AUDIT_DELETE_MEMORY = "DELETE_MEMORY";
    private static final String AUDIT_CLEAR_MEMORY = "CLEAR_MEMORY";
    private static final String METRIC_MEMORY_CONTEXT_DURATION = "data_agent_memory_context_duration";

    private final MemoryEntryRepository memoryEntryRepository;
    private final SecurityContextHelper securityContextHelper;
    private final VectorMemoryService vectorMemoryService;
    private final AuditLogService auditLogService;
    private final UserProfileMemoryService userProfileMemoryService;
    private final UserProfileMemoryRefreshService userProfileMemoryRefreshService;
    private final SemanticMemoryService semanticMemoryService;
    private final MemoryProperties memoryProperties;
    private final MeterRegistry meterRegistry;

    public MemoryGovernanceService(MemoryEntryRepository memoryEntryRepository,
            SecurityContextHelper securityContextHelper,
            VectorMemoryService vectorMemoryService,
            AuditLogService auditLogService,
            UserProfileMemoryService userProfileMemoryService,
            UserProfileMemoryRefreshService userProfileMemoryRefreshService,
            SemanticMemoryService semanticMemoryService,
            MemoryProperties memoryProperties,
            MeterRegistry meterRegistry) {
        this.memoryEntryRepository = memoryEntryRepository;
        this.securityContextHelper = securityContextHelper;
        this.vectorMemoryService = vectorMemoryService;
        this.auditLogService = auditLogService;
        this.userProfileMemoryService = userProfileMemoryService;
        this.userProfileMemoryRefreshService = userProfileMemoryRefreshService;
        this.semanticMemoryService = semanticMemoryService;
        this.memoryProperties = memoryProperties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 查询当前用户的记忆列表。
     *
     * @param tier 可选记忆层级
     * @param limit 最大返回条数
     * @return 记忆列表响应
     */
    @Transactional(readOnly = true)
    public MemoryListResponse listCurrentUserMemories(MemoryTier tier, int limit) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (!hasIdentity(tenantId, userId)) {
            return new MemoryListResponse(false, List.of(), 0L);
        }

        PageRequest page = PageRequest.of(DEFAULT_PAGE, normalizeLimit(limit));
        List<MemoryEntry> entries = tier == null
                ? memoryEntryRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId, page)
                : memoryEntryRepository.findByTenantIdAndUserIdAndTierOrderByCreatedAtDesc(tenantId, userId, tier, page);
        long total = tier == null
                ? memoryEntryRepository.countByTenantIdAndUserId(tenantId, userId)
                : memoryEntryRepository.countByTenantIdAndUserIdAndTier(tenantId, userId, tier);
        return new MemoryListResponse(true, entries.stream().map(MemoryEntryResponse::from).toList(), total);
    }

    /**
     * 根据当前用户记忆构建结构化画像。
     *
     * @return 用户记忆画像响应
     */
    @Transactional(readOnly = true)
    public UserMemoryProfileResponse getCurrentUserProfile() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (!hasIdentity(tenantId, userId)) {
            return new UserMemoryProfileResponse(false, UserMemoryProfileSnapshotResponse.empty(),
                    List.of(), List.of(), List.of(), 0L);
        }

        PageRequest page = PageRequest.of(DEFAULT_PAGE, PROFILE_LIMIT);
        List<MemoryEntry> preferences = loadSemanticType(tenantId, userId, MemoryType.PREFERENCE, page);
        List<MemoryEntry> profileFacts = loadSemanticType(tenantId, userId, MemoryType.ENTITY, page);
        List<MemoryEntry> conclusions = loadSemanticType(tenantId, userId, MemoryType.CONCLUSION, page);
        return new UserMemoryProfileResponse(
                true,
                userProfileMemoryService.getSnapshot(tenantId, userId),
                preferences.stream().map(MemoryEntryResponse::from).toList(),
                profileFacts.stream().map(MemoryEntryResponse::from).toList(),
                conclusions.stream().map(MemoryEntryResponse::from).toList(),
                memoryEntryRepository.countByTenantIdAndUserId(tenantId, userId));
    }

    /**
     * 修正当前用户的一条长期语义记忆。
     *
     * @param memoryId 记忆 ID
     * @param request 修正内容
     * @return 变更结果
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryMutationResponse updateCurrentUserMemory(
            String memoryId, SemanticMemoryUpdateRequest request) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (!hasIdentity(tenantId, userId) || !StringUtils.hasText(memoryId) || request == null) {
            return MemoryMutationResponse.failure("记忆不存在或无权限");
        }
        MemoryEntry entry = memoryEntryRepository.findByMemoryIdAndTenantIdAndUserId(memoryId, tenantId, userId)
                .orElse(null);
        if (entry == null || !MemoryTier.LONG_TERM.equals(entry.getTier()) || !isSemanticType(entry.getType())) {
            return MemoryMutationResponse.failure("仅支持修正长期语义记忆");
        }
        if (!StringUtils.hasText(entry.getSemanticKey())) {
            entry.setSemanticKey(SemanticMemoryKey.legacyKey(entry.getType(), entry.getMemoryId()));
            memoryEntryRepository.saveAndFlush(entry);
        }
        SemanticMemoryCandidate candidate = new SemanticMemoryCandidate(
                entry.getType(), entry.getSemanticKey(), request.content().trim(),
                1D, request.content().trim(), true);
        semanticMemoryService.upsertAll(tenantId, userId, entry.getSessionId(), List.of(candidate));
        auditLogService.record("UPDATE_MEMORY", AUDIT_RESOURCE_MEMORY, entry.getMemoryId(),
                AUDIT_STATUS_SUCCESS, "用户修正语义记忆");
        return MemoryMutationResponse.success("记忆已更新", 1);
    }

    /**
     * 查询当前用户记忆体系统计，用于验收和运维观测。
     *
     * @return 记忆统计响应
     */
    @Transactional(readOnly = true)
    public MemoryStatsResponse getCurrentUserStats() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (!hasIdentity(tenantId, userId)) {
            return MemoryStatsResponse.empty();
        }

        long total = memoryEntryRepository.countByTenantIdAndUserId(tenantId, userId);
        long sourceContentChars = memoryEntryRepository.sumSourceContentLengthByTenantUser(tenantId, userId);
        long storedContentChars = memoryEntryRepository.sumStoredContentLengthByTenantUser(tenantId, userId);
        UserMemoryProfileSnapshotResponse profile = userProfileMemoryService.getSnapshot(tenantId, userId);
        Timer timer = meterRegistry.find(METRIC_MEMORY_CONTEXT_DURATION).timer();
        return new MemoryStatsResponse(
                true,
                total,
                memoryEntryRepository.countByTenantIdAndUserIdAndTier(tenantId, userId, MemoryTier.WORKING),
                memoryEntryRepository.countByTenantIdAndUserIdAndTier(tenantId, userId, MemoryTier.SHORT_TERM),
                memoryEntryRepository.countByTenantIdAndUserIdAndTier(tenantId, userId, MemoryTier.LONG_TERM),
                countByType(tenantId, userId),
                sourceContentChars,
                storedContentChars,
                calculateCompressionRatio(sourceContentChars, storedContentChars),
                calculateStorageSavingRatio(sourceContentChars, storedContentChars),
                memoryProperties.getMaxMemoriesPerUser(),
                calculateQuotaUsage(total),
                memoryProperties.getShortTermRetentionDays(),
                memoryProperties.getLongTermDecayAfterDays(),
                profile.confidence(),
                timer == null ? 0L : timer.count(),
                timer == null ? 0D : timer.mean(TimeUnit.MILLISECONDS),
                timer == null ? 0D : timer.max(TimeUnit.MILLISECONDS));
    }

    /**
     * 删除当前用户的一条记忆，并在长期记忆场景下同步清理向量片段。
     *
     * @param memoryId 记忆 ID
     * @return 变更结果
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryMutationResponse deleteCurrentUserMemory(String memoryId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (!hasIdentity(tenantId, userId) || !StringUtils.hasText(memoryId)) {
            return MemoryMutationResponse.failure("记忆不存在或无权限");
        }

        return memoryEntryRepository.findByMemoryIdAndTenantIdAndUserId(memoryId, tenantId, userId)
                .map(entry -> deleteEntry(entry, tenantId))
                .orElseGet(() -> MemoryMutationResponse.failure("记忆不存在或无权限"));
    }

    /**
     * 清理当前用户指定层级的记忆；未指定层级时清理所有持久化层级。
     *
     * @param tier 可选记忆层级
     * @return 变更结果
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryMutationResponse clearCurrentUserMemories(MemoryTier tier) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (!hasIdentity(tenantId, userId)) {
            return MemoryMutationResponse.failure("当前用户身份无效");
        }

        List<MemoryEntry> entries = loadEntriesForDeletion(tenantId, userId, tier);
        entries.forEach(entry -> cleanupVectorIfNecessary(entry, tenantId));
        memoryEntryRepository.deleteAll(entries);
        memoryEntryRepository.flush();
        userProfileMemoryRefreshService.refresh(tenantId, userId);
        auditLogService.record(AUDIT_CLEAR_MEMORY, AUDIT_RESOURCE_MEMORY, tier == null ? "ALL" : tier.name(),
                AUDIT_STATUS_SUCCESS, "清理记忆数量: " + entries.size());
        return MemoryMutationResponse.success("记忆已清理", entries.size());
    }

    private MemoryMutationResponse deleteEntry(MemoryEntry entry, String tenantId) {
        cleanupVectorIfNecessary(entry, tenantId);
        memoryEntryRepository.delete(entry);
        memoryEntryRepository.flush();
        userProfileMemoryRefreshService.refresh(entry.getTenantId(), entry.getUserId());
        auditLogService.record(AUDIT_DELETE_MEMORY, AUDIT_RESOURCE_MEMORY, entry.getMemoryId(),
                AUDIT_STATUS_SUCCESS, "删除用户记忆");
        return MemoryMutationResponse.success("记忆已删除", 1);
    }

    private List<MemoryEntry> loadEntriesForDeletion(String tenantId, String userId, MemoryTier tier) {
        PageRequest page = PageRequest.of(DEFAULT_PAGE, MAX_LIMIT);
        if (tier == null) {
            return memoryEntryRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId, page);
        }
        return memoryEntryRepository.findByTenantIdAndUserIdAndTierOrderByCreatedAtDesc(tenantId, userId, tier, page);
    }

    private void cleanupVectorIfNecessary(MemoryEntry entry, String tenantId) {
        if (MemoryTier.LONG_TERM.equals(entry.getTier())) {
            vectorMemoryService.removeFromStore(VectorDocumentTypes.MEMORY, entry.getMemoryId(), tenantId);
        }
    }

    private List<MemoryEntry> loadSemanticType(
            String tenantId, String userId, MemoryType type, PageRequest page) {
        return memoryEntryRepository.findByTenantIdAndUserIdAndTierAndTypeOrderByUpdatedAtDesc(
                tenantId, userId, MemoryTier.LONG_TERM, type, page);
    }

    private boolean isSemanticType(MemoryType type) {
        return MemoryType.PREFERENCE.equals(type)
                || MemoryType.ENTITY.equals(type)
                || MemoryType.CONCLUSION.equals(type);
    }

    private int normalizeLimit(int limit) {
        int candidate = limit > 0 ? limit : DEFAULT_LIMIT;
        return Math.max(MIN_LIMIT, Math.min(candidate, MAX_LIMIT));
    }

    private Map<String, Long> countByType(String tenantId, String userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MemoryType type : MemoryType.values()) {
            long count = memoryEntryRepository.countByTenantIdAndUserIdAndType(tenantId, userId, type);
            if (count > 0) {
                counts.put(type.name(), count);
            }
        }
        return counts;
    }

    private double calculateQuotaUsage(long total) {
        int quota = memoryProperties.getMaxMemoriesPerUser();
        if (quota <= 0) {
            return 0D;
        }
        return Math.round((total * 1D / quota) * 10000D) / 10000D;
    }

    private double calculateCompressionRatio(long sourceContentChars, long storedContentChars) {
        if (sourceContentChars <= 0) {
            return 0D;
        }
        return Math.round((storedContentChars * 1D / sourceContentChars) * 10000D) / 10000D;
    }

    private double calculateStorageSavingRatio(long sourceContentChars, long storedContentChars) {
        if (sourceContentChars <= 0) {
            return 0D;
        }
        double saving = 1D - (storedContentChars * 1D / sourceContentChars);
        return Math.round(Math.max(0D, saving) * 10000D) / 10000D;
    }

    private boolean hasIdentity(String tenantId, String userId) {
        return StringUtils.hasText(tenantId) && StringUtils.hasText(userId);
    }
}
