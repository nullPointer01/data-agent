package com.ai.repository;

import com.ai.model.ConversationSessionEntity;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** ConversationSessionEntity 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface ConversationSessionRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default ConversationSessionEntity save(ConversationSessionEntity entity) {
        boolean creating = entity.getSessionId() == null || !existsById(entity.getSessionId());
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

    int insert(ConversationSessionEntity entity);
    int update(ConversationSessionEntity entity);
    Optional<ConversationSessionEntity> findById(@Param("id") String id);
    boolean existsById(@Param("id") String id);
    List<ConversationSessionEntity> findAll();
    List<ConversationSessionEntity> findAllById(@Param("ids") Collection<String> ids);
    void deleteById(@Param("id") String id);

    default void delete(ConversationSessionEntity entity) {
        deleteById(entity.getSessionId());
    }

    @Transactional
    default List<ConversationSessionEntity> saveAll(Iterable<ConversationSessionEntity> entities) {
        List<ConversationSessionEntity> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    Optional<ConversationSessionEntity> findOwnedForUpdate(@Param("sessionId") String sessionId,
            @Param("userId") String userId,
            @Param("tenantId") String tenantId);

    List<ConversationSessionEntity> findByUserIdOrderByLastAccessAtDesc(@Param("userId") String userId);

    List<ConversationSessionEntity> findByUserIdAndStatusOrderByLastAccessAtDesc(@Param("userId") String userId, @Param("status") String status);

    List<ConversationSessionEntity> findByTenantIdOrderByLastAccessAtDesc(@Param("tenantId") String tenantId);

    List<ConversationSessionEntity> findByUserIdAndStatus(@Param("userId") String userId, @Param("status") String status);

    Optional<ConversationSessionEntity> findBySessionIdAndUserId(@Param("sessionId") String sessionId, @Param("userId") String userId);

    Optional<ConversationSessionEntity> findBySessionIdAndUserIdAndTenantId(@Param("sessionId") String sessionId,
            @Param("userId") String userId,
            @Param("tenantId") String tenantId);

    long countByUserId(@Param("userId") String userId);
}
