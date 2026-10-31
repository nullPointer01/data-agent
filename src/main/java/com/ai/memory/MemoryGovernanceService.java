package com.ai.memory;

import com.ai.memory.dto.MemoryEntryResponse;
import com.ai.api.CursorPageResponse;
import com.ai.api.ListPaging;
import com.ai.api.RecordCursor;
import com.ai.memory.dto.MemoryMutationResponse;
import com.ai.memory.dto.MemoryStatsResponse;
import com.ai.memory.dto.SemanticMemoryUpdateRequest;
import com.ai.memory.dto.UserMemoryProfileResponse;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.repository.MemoryEntryRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.service.AuditLogService;
import com.ai.indexing.IndexTask;
import com.ai.indexing.IndexTaskStore;
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
    private static final int MAX_LIMIT = 200;
    private static final int PROFILE_LIMIT = 20;
    private static final String AUDIT_RESOURCE_MEMORY = "memory";
    private static final String AUDIT_STATUS_SUCCESS = "SUCCESS";
    private static final String AUDIT_DELETE_MEMORY = "DELETE_MEMORY";
    private static final String AUDIT_CLEAR_MEMORY = "CLEAR_MEMORY";
    private static final String METRIC_MEMORY_CONTEXT_DURATION = "data_agent_memory_context_duration";

    private final MemoryEntryRepository memoryEntryRepository;
    private final SecurityContextHelper securityContextHelper;
    private final IndexTaskStore indexTasks;
    private final AuditLogService auditLogService;
    private final UserProfileMemoryService userProfileMemoryService;
    private final UserProfileMemoryRefreshService userProfileMemoryRefreshService;
    private final SemanticMemoryService semanticMemoryService;
    private final MemoryProperties memoryProperties;
    private final MeterRegistry meterRegistry;
    private final MemoryWriteGuard writeGuard;

    public MemoryGovernanceService(MemoryEntryRepository memoryEntryRepository,
            SecurityContextHelper securityContextHelper,
            IndexTaskStore indexTasks,
            AuditLogService auditLogService,
            UserProfileMemoryService userProfileMemoryService,
            UserProfileMemoryRefreshService userProfileMemoryRefreshService,
            SemanticMemoryService semanticMemoryService,
            MemoryProperties memoryProperties,
            MeterRegistry meterRegistry, MemoryWriteGuard writeGuard) {
        this.memoryEntryRepository = memoryEntryRepository;
        this.securityContextHelper = securityContextHelper;
        this.indexTasks = indexTasks;
        this.auditLogService = auditLogService;
        this.userProfileMemoryService = userProfileMemoryService;
        this.userProfileMemoryRefreshService = userProfileMemoryRefreshService;
        this.semanticMemoryService = semanticMemoryService;
        this.memoryProperties = memoryProperties;
        this.meterRegistry = meterRegistry;
        this.writeGuard = writeGuard;
    }

    /**
     * 查询当前用户的记忆列表。
     *
     * @param tier 可选记忆层级
     * @param size 每页数量
     * @param after 上一页返回的游标
     * @return 记忆列表响应
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<MemoryEntryResponse> listCurrentUserMemories(MemoryTier tier, int size, String after) {
        int pageSize = ListPaging.size(size);
        var cursor = RecordCursor.decode(after);
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (!hasIdentity(tenantId, userId)) {
            return new CursorPageResponse<>(false, List.of(), pageSize, false, null);
        }

        var entries = memoryEntryRepository.findCursorPage(tenantId, userId, tier, cursor, pageSize + 1);
        var page = CursorPageResponse.fromFetched(entries, pageSize,
                entry -> new RecordCursor(entry.getCreatedAt(), entry.getMemoryId()));
        return new CursorPageResponse<>(true, responses(page.items(), tenantId, userId),
                page.size(), page.hasMore(), page.nextCursor());
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
                responses(preferences, tenantId, userId),
                responses(profileFacts, tenantId, userId),
                responses(conclusions, tenantId, userId),
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
        writeGuard.lock(tenantId, userId);
        MemoryEntry entry = memoryEntryRepository.findByMemoryIdAndTenantIdAndUserId(memoryId, tenantId, userId)
                .orElse(null);
        if (entry == null || !MemoryTier.LONG_TERM.equals(entry.getTier()) || !isSemanticType(entry.getType())) {
            return MemoryMutationResponse.failure("仅支持修正长期语义记忆");
        }
        if (!StringUtils.hasText(entry.getSemanticKey())) {
            entry.setSemanticKey(SemanticMemoryKey.legacyKey(entry.getType(), entry.getMemoryId()));
            memoryEntryRepository.save(entry);
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
     * 删除当前用户的一条记忆，并同事务登记长期记忆的向量清理任务。
     *
     * @param memoryId 记忆 ID
     * @return 变更结果
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryMutationResponse deleteCurrentUserMemory(String memoryId) {
        return deleteConfirmedMemory(memoryId, null);
    }

    /** 工具在事务外确认删除意图，事务内重验目标未被修正，避免删掉刚更新的内容。 */
    @Transactional(rollbackFor = Exception.class)
    public MemoryMutationResponse deleteConfirmedMemory(String memoryId, String expectedContent) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (!hasIdentity(tenantId, userId) || !StringUtils.hasText(memoryId)) {
            return MemoryMutationResponse.failure("记忆不存在或无权限");
        }

        writeGuard.lock(tenantId, userId);
        return memoryEntryRepository.findByMemoryIdAndTenantIdAndUserId(memoryId, tenantId, userId)
                .filter(entry -> expectedContent == null || expectedContent.equals(entry.getContent()))
                .map(this::deleteEntry)
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

        writeGuard.advance(tenantId, userId);
        int count = 0;
        List<MemoryEntry> entries;
        do {
            entries = loadEntriesForDeletion(tenantId, userId, tier);
            entries.forEach(this::scheduleVectorCleanup);
            memoryEntryRepository.deleteAll(entries);
            count += entries.size();
        } while (entries.size() == MAX_LIMIT);
        userProfileMemoryRefreshService.refresh(tenantId, userId);
        auditLogService.record(AUDIT_CLEAR_MEMORY, AUDIT_RESOURCE_MEMORY, tier == null ? "ALL" : tier.name(),
                AUDIT_STATUS_SUCCESS, "清理记忆数量: " + count);
        return MemoryMutationResponse.success("记忆已清理", count);
    }

    /**
     * 重置当前用户的结构化画像及对应长期证据，保留其他记忆与会话。
     *
     * @return 重置结果与删除的画像证据数量
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryMutationResponse resetCurrentUserProfile() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (!hasIdentity(tenantId, userId)) {
            return MemoryMutationResponse.failure("当前用户身份无效");
        }

        // 复用遗忘栅栏，让重置前尚未提交的后台提取任务失效。
        writeGuard.advance(tenantId, userId);
        int count = 0;
        List<MemoryEntry> entries;
        do {
            entries = memoryEntryRepository.findProfileEvidenceForReset(tenantId, userId,
                    SemanticMemoryKey.PROFILE_KEYS, SemanticMemoryKey.PROFILE_KEY_PREFIXES, MAX_LIMIT);
            entries.forEach(this::scheduleVectorCleanup);
            memoryEntryRepository.deleteAll(entries);
            count += entries.size();
        } while (entries.size() == MAX_LIMIT);
        userProfileMemoryRefreshService.refresh(tenantId, userId);
        auditLogService.record("RESET_USER_PROFILE", AUDIT_RESOURCE_MEMORY, "PROFILE",
                AUDIT_STATUS_SUCCESS, "重置画像，删除对应长期记忆数量: " + count);
        return MemoryMutationResponse.success("用户画像已重置，已清除 " + count + " 条画像依据", count);
    }

    private MemoryMutationResponse deleteEntry(MemoryEntry entry) {
        writeGuard.advance(entry.getTenantId(), entry.getUserId());
        scheduleVectorCleanup(entry);
        memoryEntryRepository.delete(entry);
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

    private void scheduleVectorCleanup(MemoryEntry entry) {
        if (MemoryTier.LONG_TERM.equals(entry.getTier())) {
            indexTasks.enqueue(IndexTask.Kind.MEMORY, entry.getTenantId(), entry.getUserId(), entry.getMemoryId());
        }
    }

    private List<MemoryEntry> loadSemanticType(
            String tenantId, String userId, MemoryType type, PageRequest page) {
        return memoryEntryRepository.findByTenantIdAndUserIdAndTierAndTypeOrderByUpdatedAtDesc(
                tenantId, userId, MemoryTier.LONG_TERM, type, page);
    }

    /** 批量取得本页索引状态，避免每条记忆查询一次任务表。 */
    private List<MemoryEntryResponse> responses(List<MemoryEntry> entries, String tenant, String user) {
        var states = indexTasks.memoryStatuses(tenant, user, entries.stream().map(MemoryEntry::getMemoryId).toList());
        return entries.stream().map(entry -> MemoryEntryResponse.from(entry,
                entry.getTier() == MemoryTier.LONG_TERM ? states.getOrDefault(entry.getMemoryId(), "NOT_INDEXED")
                        : "NOT_REQUIRED")).toList();
    }

    private boolean isSemanticType(MemoryType type) {
        return MemoryType.PREFERENCE.equals(type)
                || MemoryType.ENTITY.equals(type)
                || MemoryType.CONCLUSION.equals(type);
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
