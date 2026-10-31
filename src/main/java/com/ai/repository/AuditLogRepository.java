package com.ai.repository;

import com.ai.model.AuditLog;
import com.ai.api.RecordCursor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** AuditLog 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface AuditLogRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default AuditLog save(AuditLog entity) {
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

    int insert(AuditLog entity);
    int update(AuditLog entity);
    Optional<AuditLog> findById(@Param("id") Long id);
    boolean existsById(@Param("id") Long id);
    List<AuditLog> findAll();
    /** 按本人租户和可选用户、搜索条件向历史方向翻页。 */
    List<AuditLog> findCursorPage(@Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("query") String query, @Param("cursor") RecordCursor cursor, @Param("limit") int limit);
    List<AuditLog> findAllById(@Param("ids") Collection<Long> ids);
    void deleteById(@Param("id") Long id);

    default void delete(AuditLog entity) {
        deleteById(entity.getId());
    }

    @Transactional
    default List<AuditLog> saveAll(Iterable<AuditLog> entities) {
        List<AuditLog> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

}
