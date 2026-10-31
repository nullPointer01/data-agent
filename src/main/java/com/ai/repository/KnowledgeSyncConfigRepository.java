package com.ai.repository;

import com.ai.model.KnowledgeSyncConfig;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** KnowledgeSyncConfig 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface KnowledgeSyncConfigRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default KnowledgeSyncConfig save(KnowledgeSyncConfig entity) {
        boolean creating = entity.getId() == null || !existsById(entity.getId());
        if (creating) {
            entity.onCreate();
            insert(entity);
        } else {
            if (update(entity) != 1) {
                throw new OptimisticLockingFailureException("记录已变化，请重新读取后保存");
            }
        }
        return entity;
    }

    int insert(KnowledgeSyncConfig entity);
    int update(KnowledgeSyncConfig entity);
    Optional<KnowledgeSyncConfig> findById(@Param("id") Long id);
    boolean existsById(@Param("id") Long id);
    List<KnowledgeSyncConfig> findAll();
    List<KnowledgeSyncConfig> findAllById(@Param("ids") Collection<Long> ids);
    void deleteById(@Param("id") Long id);

    default void delete(KnowledgeSyncConfig entity) {
        deleteById(entity.getId());
    }

    @Transactional
    default List<KnowledgeSyncConfig> saveAll(Iterable<KnowledgeSyncConfig> entities) {
        List<KnowledgeSyncConfig> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    List<KnowledgeSyncConfig> findByEnabledTrue();

    Optional<KnowledgeSyncConfig> findByKnowledgeIdAndTenantId(@Param("knowledgeId") String knowledgeId, @Param("tenantId") String tenantId);

    List<KnowledgeSyncConfig> findByTenantId(@Param("tenantId") String tenantId);
}
