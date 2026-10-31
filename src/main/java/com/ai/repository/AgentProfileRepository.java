package com.ai.repository;

import com.ai.model.AgentProfile;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** AgentProfile 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface AgentProfileRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default AgentProfile save(AgentProfile entity) {
        boolean creating = entity.getAgentId() == null || !existsById(entity.getAgentId());
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

    int insert(AgentProfile entity);
    int update(AgentProfile entity);
    Optional<AgentProfile> findById(@Param("id") String id);
    boolean existsById(@Param("id") String id);
    List<AgentProfile> findAll();
    List<AgentProfile> findAllById(@Param("ids") Collection<String> ids);
    void deleteById(@Param("id") String id);

    default void delete(AgentProfile entity) {
        deleteById(entity.getAgentId());
    }

    @Transactional
    default List<AgentProfile> saveAll(Iterable<AgentProfile> entities) {
        List<AgentProfile> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    List<AgentProfile> findByTenantIdOrderByUpdatedAtDesc(@Param("tenantId") String tenantId);

    List<AgentProfile> findByTenantIdAndCreatedByOrderByUpdatedAtDesc(@Param("tenantId") String tenantId, @Param("createdBy") String createdBy);

    Optional<AgentProfile> findByAgentIdAndTenantId(@Param("agentId") String agentId, @Param("tenantId") String tenantId);

    Optional<AgentProfile> findByAgentIdAndTenantIdAndCreatedBy(@Param("agentId") String agentId,
            @Param("tenantId") String tenantId,
            @Param("createdBy") String createdBy);

    Optional<AgentProfile> findFirstByTenantIdAndCreatedByAndDefaultAgentTrueOrderByUpdatedAtDesc(@Param("tenantId") String tenantId,
            @Param("createdBy") String createdBy);
    /** 在数据库内完成搜索、作用域过滤和分页；计数与列表共用条件。 */
    List<AgentProfile> findListPage(@Param("filter") com.ai.api.CatalogListFilter filter,
            @Param("pageable") org.springframework.data.domain.Pageable pageable);
    long countList(@Param("filter") com.ai.api.CatalogListFilter filter);
    /** 子 Agent 目录先在 SQL 限定本人专家，运行时权限再逐项复核。 */
    List<AgentProfile> findCapabilityCandidates(@Param("tenantId") String tenantId,
            @Param("ownerId") String ownerId, @Param("editingAgentId") String editingAgentId,
            @Param("query") String query, @Param("after") String after, @Param("limit") int limit);
}
