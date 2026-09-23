package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.security.SecurityContextHelper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Agent 执行时使用的统一记忆门面。
 *
 * @author data-agent
 */
@Service
public class MemoryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryManager.class);
    private static final int LONG_TERM_CONTEXT_TOP_K = 5;

    private final WorkingMemory workingMemory;
    private final ShortTermMemory shortTermMemory;
    private final LongTermMemory longTermMemory;
    private final SecurityContextHelper securityContextHelper;
    private final MeterRegistry meterRegistry;
    private final MemoryQuotaService memoryQuotaService;
    private final UserProfileMemoryService userProfileMemoryService;
    private final UserProfileMemoryRefreshService userProfileMemoryRefreshService;

    public MemoryManager(WorkingMemory workingMemory,
            ShortTermMemory shortTermMemory,
            LongTermMemory longTermMemory,
            SecurityContextHelper securityContextHelper,
            MeterRegistry meterRegistry,
            MemoryQuotaService memoryQuotaService,
            UserProfileMemoryService userProfileMemoryService,
            UserProfileMemoryRefreshService userProfileMemoryRefreshService) {
        this.workingMemory = workingMemory;
        this.shortTermMemory = shortTermMemory;
        this.longTermMemory = longTermMemory;
        this.securityContextHelper = securityContextHelper;
        this.meterRegistry = meterRegistry;
        this.memoryQuotaService = memoryQuotaService;
        this.userProfileMemoryService = userProfileMemoryService;
        this.userProfileMemoryRefreshService = userProfileMemoryRefreshService;
    }

    /**
     * 为当前租户用户写入指定层级记忆。
     *
     * @param request 记忆写入请求
     * @return 持久化后的记忆，身份缺失或写入工作记忆时返回 null
     */
    public MemoryEntry capture(MemoryCaptureRequest request) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
            return null;
        }
        MemoryCaptureRequest normalized = request.normalize();
        if (MemoryTier.WORKING.equals(normalized.tier())) {
            workingMemory.save(tenantId, userId, normalized.sessionId(), normalized.effectiveContent());
            return null;
        }
        memoryQuotaService.pruneBeforeCapture(tenantId, userId);
        if (MemoryTier.LONG_TERM.equals(normalized.tier())) {
            MemoryEntry entry = longTermMemory.capture(tenantId, userId, normalized);
            refreshUserProfile(tenantId, userId);
            return entry;
        }
        return shortTermMemory.capture(tenantId, userId, normalized);
    }

    /**
     * 按当前用户检索相关长期记忆；预加载和 searchMemory 共用同一身份与召回口径。
     *
     * @param query 补全指代后的检索问题
     * @return 相关长期记忆，无身份或无命中时返回空文本；检索故障由调用方决定如何降级
     */
    public String recallSemanticMemory(String query) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String tenantId = securityContextHelper.getCurrentTenantId();
            String userId = securityContextHelper.getCurrentUserId();
            if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId) || !StringUtils.hasText(query)) {
                return "";
            }
            return longTermMemory.recall(tenantId, userId, query, LONG_TERM_CONTEXT_TOP_K);
        } finally {
            sample.stop(meterRegistry.timer("data_agent_memory_context_duration"));
        }
    }

    /** 读取当前会话的工作状态；独立于跨会话语义检索，Redis 故障不影响其他上下文。 */
    public String loadWorkingMemory(String sessionId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) {
            return "";
        }
        return loadMemoryPart("工作记忆", () -> workingMemory.get(tenantId, userId, sessionId), "");
    }

    /**
     * 独立读取稳定用户画像，不触发工作记忆、短期记忆或向量检索。
     *
     * @return 当前用户画像；身份缺失或读取失败时返回空画像
     */
    public UserMemoryProfileSnapshotResponse loadUserProfile() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
            return UserMemoryProfileSnapshotResponse.empty();
        }
        return loadMemoryPart("用户画像",
                () -> userProfileMemoryService.getSnapshot(tenantId, userId),
                UserMemoryProfileSnapshotResponse.empty());
    }

    /**
     * 保存当前执行状态到工作记忆。
     *
     * @param sessionId 会话 ID
     * @param content 状态内容
     */
    public void saveWorkingMemory(String sessionId, String content) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        workingMemory.save(tenantId, userId, sessionId, content);
    }

    private void refreshUserProfile(String tenantId, String userId) {
        userProfileMemoryRefreshService.refresh(tenantId, userId);
    }

    /**
     * 独立加载单类记忆，防止一个外部依赖故障清空其他已经可用的上下文。
     *
     * @param part 记忆模块名称
     * @param loader 记忆加载动作
     * @param fallback 加载失败时的空值
     * @param <T> 记忆结果类型
     * @return 加载结果或对应空值
     */
    private <T> T loadMemoryPart(String part, Supplier<T> loader, T fallback) {
        try {
            return loader.get();
        } catch (RuntimeException e) {
            LOGGER.warn("加载{}失败，使用空值降级: exception={}, message={}",
                    part, e.getClass().getSimpleName(), e.getMessage());
            return fallback;
        }
    }
}
