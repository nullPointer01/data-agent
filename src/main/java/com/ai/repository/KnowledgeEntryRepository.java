package com.ai.repository;

import com.ai.model.KnowledgeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 知识条目仓储。
 *
 * @author data-agent
 */
public interface KnowledgeEntryRepository extends JpaRepository<KnowledgeEntry, String> {

    /**
     * 查询租户下的知识条目，按创建时间倒序。
     *
     * @param tenantId 租户编号
     * @return 知识条目
     */
    List<KnowledgeEntry> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /**
     * 按租户查询单条知识。
     *
     * @param knowledgeId 知识编号
     * @param tenantId 租户编号
     * @return 知识条目
     */
    Optional<KnowledgeEntry> findByKnowledgeIdAndTenantId(String knowledgeId, String tenantId);

}
