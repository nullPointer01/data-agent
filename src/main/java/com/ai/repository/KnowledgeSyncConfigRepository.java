package com.ai.repository;

import com.ai.model.KnowledgeSyncConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 知识同步配置仓储。
 *
 * @author data-agent
 */
public interface KnowledgeSyncConfigRepository extends JpaRepository<KnowledgeSyncConfig, Long> {

    /**
     * 查询已启用的自动同步配置。
     *
     * @return 已启用配置列表
     */
    List<KnowledgeSyncConfig> findByEnabledTrue();

    /**
     * 按知识 ID 和租户 ID 查询同步配置。
     *
     * @param knowledgeId 知识 ID
     * @param tenantId 租户 ID
     * @return 同步配置
     */
    Optional<KnowledgeSyncConfig> findByKnowledgeIdAndTenantId(String knowledgeId, String tenantId);

    /**
     * 按租户 ID 查询同步配置。
     *
     * @param tenantId 租户 ID
     * @return 租户同步配置列表
     */
    List<KnowledgeSyncConfig> findByTenantId(String tenantId);
}
