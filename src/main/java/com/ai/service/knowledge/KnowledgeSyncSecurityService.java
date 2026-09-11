package com.ai.service.knowledge;

import com.ai.model.KnowledgeEntry;
import com.ai.model.KnowledgeSyncConfig;
import com.ai.repository.KnowledgeEntryRepository;
import com.ai.repository.KnowledgeSyncConfigRepository;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Component;

/**
 * 集中进行知识同步的用户和租户所有权检查。
 *
 * @author data-agent
 */
@Component
public class KnowledgeSyncSecurityService {

    private final KnowledgeSyncConfigRepository syncConfigRepository;
    private final KnowledgeEntryRepository knowledgeEntryRepository;
    private final SecurityContextHelper securityContextHelper;

    public KnowledgeSyncSecurityService(KnowledgeSyncConfigRepository syncConfigRepository,
            KnowledgeEntryRepository knowledgeEntryRepository,
            SecurityContextHelper securityContextHelper) {
        this.syncConfigRepository = syncConfigRepository;
        this.knowledgeEntryRepository = knowledgeEntryRepository;
        this.securityContextHelper = securityContextHelper;
    }

    /**
     * 断言当前用户拥有知识条目。
     *
     * @param knowledgeId 知识 ID
     * @return 拥有的知识条目
     */
    public KnowledgeEntry assertCurrentUserKnowledge(String knowledgeId) {
        String tenantId = currentTenantIdOrThrow();
        String userId = currentUserIdOrThrow();
        return knowledgeEntryRepository.findByKnowledgeIdAndTenantIdAndCreatedBy(knowledgeId, tenantId, userId)
                .orElseThrow(() -> new SecurityException("知识条目不存在或无权访问"));
    }

    /**
     * 查找当前用户知识条目的同步配置。
     *
     * @param knowledgeId 知识 ID
     * @return 拥有的同步配置
     */
    public KnowledgeSyncConfig findOwnedSyncConfig(String knowledgeId) {
        String tenantId = currentTenantIdOrThrow();
        assertCurrentUserKnowledge(knowledgeId);
        return findTenantSyncConfig(knowledgeId, tenantId);
    }

    /**
     * Finds a sync config for the supplied tenant.
     *
     * @param knowledgeId knowledge id
     * @param tenantId tenant id
     * @return tenant sync config
     */
    public KnowledgeSyncConfig findTenantSyncConfig(String knowledgeId, String tenantId) {
        if (tenantId == null) {
            throw new SecurityException("无权访问该同步配置");
        }
        return syncConfigRepository.findByKnowledgeIdAndTenantId(knowledgeId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("未配置自动同步: " + knowledgeId));
    }

    /**
     * Asserts that supplied tenant owns a knowledge entry.
     *
     * @param knowledgeId knowledge id
     * @param tenantId tenant id
     * @return owned knowledge entry
     */
    public KnowledgeEntry assertTenantKnowledge(String knowledgeId, String tenantId) {
        return knowledgeEntryRepository.findByKnowledgeIdAndTenantId(knowledgeId, tenantId)
                .orElseThrow(() -> new SecurityException("知识条目不存在或无权访问"));
    }

    /**
     * Gets current tenant id or throws a clear security error.
     *
     * @return current tenant id
     */
    public String currentTenantIdOrThrow() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException("未登录或租户上下文为空");
        }
        return tenantId;
    }

    /**
     * 获取当前用户 ID，缺少认证上下文时拒绝操作。
     *
     * @return 当前用户 ID
     */
    public String currentUserIdOrThrow() {
        String userId = securityContextHelper.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new SecurityException("未登录或用户上下文为空");
        }
        return userId;
    }
}
