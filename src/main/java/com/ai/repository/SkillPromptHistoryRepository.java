package com.ai.repository;

import com.ai.model.SkillPromptHistory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** SkillPromptHistory 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface SkillPromptHistoryRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default SkillPromptHistory save(SkillPromptHistory entity) {
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

    int insert(SkillPromptHistory entity);
    int update(SkillPromptHistory entity);
    Optional<SkillPromptHistory> findById(@Param("id") Long id);
    boolean existsById(@Param("id") Long id);
    List<SkillPromptHistory> findAll();
    List<SkillPromptHistory> findAllById(@Param("ids") Collection<Long> ids);
    void deleteById(@Param("id") Long id);

    default void delete(SkillPromptHistory entity) {
        deleteById(entity.getId());
    }

    @Transactional
    default List<SkillPromptHistory> saveAll(Iterable<SkillPromptHistory> entities) {
        List<SkillPromptHistory> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    List<SkillPromptHistory> findHistoryPage(@Param("skillId") String skillId, @Param("tenantId") String tenantId,
            @Param("pageable") org.springframework.data.domain.Pageable pageable);
    long countHistory(@Param("skillId") String skillId, @Param("tenantId") String tenantId);
    Optional<SkillPromptHistory> findVersion(@Param("skillId") String skillId, @Param("tenantId") String tenantId,
            @Param("version") int version);

    Optional<SkillPromptHistory> findTopBySkillIdAndTenantIdOrderByVersionDesc(@Param("skillId") String skillId, @Param("tenantId") String tenantId);
}
