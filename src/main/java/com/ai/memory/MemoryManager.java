package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import com.ai.memory.dto.MemoryContext;
import com.ai.memory.dto.MemoryEntrySummary;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.security.SecurityContextHelper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Agent 执行时使用的统一记忆门面。
 *
 * @author data-agent
 */
@Service
public class MemoryManager {

    private static final int SHORT_TERM_CONTEXT_LIMIT = 5;
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
     * 组装用于注入 Prompt 的记忆上下文。
     *
     * @param sessionId 当前会话 ID
     * @param query 当前用户问题
     * @return 记忆上下文
     */
    public MemoryContext buildContext(String sessionId, String query) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String tenantId = securityContextHelper.getCurrentTenantId();
            String userId = securityContextHelper.getCurrentUserId();
            if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
                return MemoryContext.empty();
            }

            String working = workingMemory.get(tenantId, userId, sessionId);
            List<MemoryEntrySummary> shortTerm = shortTermMemory.recall(tenantId, userId, SHORT_TERM_CONTEXT_LIMIT);
            String longTerm = longTermMemory.recall(tenantId, userId, query, LONG_TERM_CONTEXT_TOP_K);
            UserMemoryProfileSnapshotResponse userProfile = userProfileMemoryService.getSnapshot(tenantId, userId);
            return new MemoryContext(working, shortTerm, longTerm, userProfile);
        } finally {
            sample.stop(meterRegistry.timer("data_agent_memory_context_duration"));
        }
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
}
