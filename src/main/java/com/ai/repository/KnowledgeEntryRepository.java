package com.ai.repository;

import com.ai.model.KnowledgeEntry;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** KnowledgeEntry 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface KnowledgeEntryRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default KnowledgeEntry save(KnowledgeEntry entity) {
        boolean creating = entity.getKnowledgeId() == null || !existsById(entity.getKnowledgeId());
        if (creating) {
            entity.onCreate();
            insert(entity);
        } else {
            entity.onUpdate();
            if (update(entity) != 1) {
                throw new OptimisticLockingFailureException("记录已变化，请重新读取后保存");
            }
        }
        return entity;
    }

    int insert(KnowledgeEntry entity);
    int update(KnowledgeEntry entity);
    Optional<KnowledgeEntry> findById(@Param("id") String id);
    boolean existsById(@Param("id") String id);
    List<KnowledgeEntry> findAll();
    List<KnowledgeEntry> findAllById(@Param("ids") Collection<String> ids);
    void deleteById(@Param("id") String id);

    default void delete(KnowledgeEntry entity) {
        deleteById(entity.getKnowledgeId());
    }

    @Transactional
    default List<KnowledgeEntry> saveAll(Iterable<KnowledgeEntry> entities) {
        List<KnowledgeEntry> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    Optional<KnowledgeEntry> findByKnowledgeIdAndTenantId(@Param("knowledgeId") String knowledgeId, @Param("tenantId") String tenantId);

    Optional<KnowledgeEntry> findByKnowledgeIdAndTenantIdAndCreatedBy(@Param("knowledgeId") String knowledgeId,
            @Param("tenantId") String tenantId,
            @Param("createdBy") String createdBy);
    /** 在数据库内完成搜索、作用域过滤和分页；计数与列表共用条件。 */
    List<KnowledgeEntry> findListPage(@Param("filter") com.ai.api.CatalogListFilter filter,
            @Param("pageable") org.springframework.data.domain.Pageable pageable);
    long countList(@Param("filter") com.ai.api.CatalogListFilter filter);
    KnowledgeTotals totals(@Param("tenantId") String tenantId, @Param("userId") String userId);

    /** 只聚合计数，不为统计读取全部知识正文。 */
    record KnowledgeTotals(int count, long chunks, long characters) { }
}
