package com.ai.service.knowledge;

import com.ai.model.KnowledgeEntry;
import com.ai.model.KnowledgeSyncConfig;
import com.ai.repository.KnowledgeEntryRepository;
import com.ai.repository.KnowledgeSyncConfigRepository;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Centralizes tenant ownership checks for knowledge synchronization.
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
     * Asserts that current tenant owns a knowledge entry.
     *
     * @param knowledgeId knowledge id
     * @return owned knowledge entry
     */
    public KnowledgeEntry assertCurrentTenantKnowledge(String knowledgeId) {
        String tenantId = currentTenantIdOrThrow();
        return assertTenantKnowledge(knowledgeId, tenantId);
    }

    /**
     * Finds a sync config owned by current tenant.
     *
     * @param knowledgeId knowledge id
     * @return owned sync config
     */
    public KnowledgeSyncConfig findOwnedSyncConfig(String knowledgeId) {
        String tenantId = currentTenantIdOrThrow();
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
        KnowledgeEntry entry = knowledgeEntryRepository.findById(knowledgeId)
                .orElseThrow(() -> new IllegalArgumentException("知识条目不存在: " + knowledgeId));
        if (!Objects.equals(tenantId, entry.getTenantId())) {
            throw new SecurityException("无权访问该知识条目");
        }
        return entry;
    }

    /**
     * Gets current tenant id or throws a clear security error.
     *
     * @return current tenant id
     */
    public String currentTenantIdOrThrow() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        if (tenantId == null) {
            throw new SecurityException("未登录或租户上下文为空");
        }
        return tenantId;
    }
}
