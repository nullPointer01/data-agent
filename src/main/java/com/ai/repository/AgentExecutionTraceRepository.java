package com.ai.repository;

import com.ai.model.AgentExecutionTrace;
import com.ai.api.RecordCursor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** AgentExecutionTrace 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface AgentExecutionTraceRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default AgentExecutionTrace save(AgentExecutionTrace entity) {
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

    int insert(AgentExecutionTrace entity);
    int update(AgentExecutionTrace entity);
    Optional<AgentExecutionTrace> findById(@Param("id") Long id);
    boolean existsById(@Param("id") Long id);
    List<AgentExecutionTrace> findAll();
    /** 列表只读摘要列，完整执行证据由详情接口读取。 */
    List<AgentExecutionTrace> findCursorPage(@Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("query") String query, @Param("cursor") RecordCursor cursor, @Param("limit") int limit);
    List<AgentExecutionTrace> findAllById(@Param("ids") Collection<Long> ids);
    void deleteById(@Param("id") Long id);

    default void delete(AgentExecutionTrace entity) {
        deleteById(entity.getId());
    }

    @Transactional
    default List<AgentExecutionTrace> saveAll(Iterable<AgentExecutionTrace> entities) {
        List<AgentExecutionTrace> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    List<AgentExecutionTrace> findByTenantIdOrderByCreatedAtDesc(@Param("tenantId") String tenantId, @Param("pageable") Pageable pageable);

    Optional<AgentExecutionTrace> findByTraceIdAndTenantId(@Param("traceId") String traceId, @Param("tenantId") String tenantId);
}
