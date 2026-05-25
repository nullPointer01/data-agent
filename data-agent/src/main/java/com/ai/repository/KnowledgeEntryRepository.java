package com.ai.repository;

import com.ai.model.KnowledgeEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * JPA 全文检索兜底查询。
     *
     * @param tenantId 租户编号
     * @param keyword 已转义的检索词
     * @param pageable 分页参数
     * @return 命中的知识条目
     */
    @Query(value = """
            SELECT * FROM knowledge_entry k
            WHERE k.tenant_id = :tenantId
              AND (
                LOWER(k.name) LIKE :pattern
                OR (k.description IS NOT NULL AND LOWER(k.description) LIKE :pattern)
                OR (k.content IS NOT NULL AND LOWER(k.content) LIKE :pattern)
              )
            ORDER BY k.updated_at DESC, k.created_at DESC
            """, nativeQuery = true)
    List<KnowledgeEntry> searchByTenantAndKeyword(@Param("tenantId") String tenantId,
            @Param("pattern") String pattern, Pageable pageable);
}
