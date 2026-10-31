package com.ai.repository;

import com.ai.model.SkillConfig;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** SkillConfig 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface SkillConfigRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default SkillConfig save(SkillConfig entity) {
        boolean creating = entity.getSkillId() == null || !existsById(entity.getSkillId());
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

    int insert(SkillConfig entity);
    int update(SkillConfig entity);
    Optional<SkillConfig> findById(@Param("id") String id);
    boolean existsById(@Param("id") String id);
    List<SkillConfig> findAll();
    List<SkillConfig> findAllById(@Param("ids") Collection<String> ids);
    void deleteById(@Param("id") String id);

    default void delete(SkillConfig entity) {
        deleteById(entity.getSkillId());
    }

    @Transactional
    default List<SkillConfig> saveAll(Iterable<SkillConfig> entities) {
        List<SkillConfig> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    List<SkillConfig> findByTenantId(@Param("tenantId") String tenantId);

    Optional<SkillConfig> findBySkillIdAndTenantId(@Param("skillId") String skillId, @Param("tenantId") String tenantId);
    /** 候选确认时锁定本人记录，重复点击只发布一次。 */
    Optional<SkillConfig> findOwnedForUpdate(@Param("skillId") String skillId,
            @Param("tenantId") String tenantId, @Param("userId") String userId);
    Optional<SkillConfig> findByLearningRun(@Param("tenantId") String tenantId,
            @Param("userId") String userId, @Param("runId") String runId);
    long countPending(@Param("tenantId") String tenantId, @Param("userId") String userId);
    List<SkillConfig> findOwnedPage(@Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("pageable") org.springframework.data.domain.Pageable pageable);
    long countOwned(@Param("tenantId") String tenantId, @Param("userId") String userId);
    /** 在数据库内完成搜索、作用域过滤和分页；计数与列表共用条件。 */
    List<SkillConfig> findListPage(@Param("filter") com.ai.api.CatalogListFilter filter,
            @Param("viewerId") String viewerId,
            @Param("pageable") org.springframework.data.domain.Pageable pageable);
    long countList(@Param("filter") com.ai.api.CatalogListFilter filter, @Param("viewerId") String viewerId);
    /** 配置目录按稳定 ID 游标读取，单次最多 size + 1 个候选。 */
    List<SkillConfig> findCapabilityCandidates(@Param("tenantId") String tenantId,
            @Param("query") String query, @Param("after") String after, @Param("limit") int limit);
}
