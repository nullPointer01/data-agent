package com.ai.repository;

import com.ai.model.ConversationMessage;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** ConversationMessage 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface ConversationMessageRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default ConversationMessage save(ConversationMessage entity) {
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

    int insert(ConversationMessage entity);
    int update(ConversationMessage entity);
    Optional<ConversationMessage> findById(@Param("id") Long id);
    boolean existsById(@Param("id") Long id);
    List<ConversationMessage> findAll();
    List<ConversationMessage> findAllById(@Param("ids") Collection<Long> ids);
    void deleteById(@Param("id") Long id);

    default void delete(ConversationMessage entity) {
        deleteById(entity.getId());
    }

    @Transactional
    default List<ConversationMessage> saveAll(Iterable<ConversationMessage> entities) {
        List<ConversationMessage> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    List<ConversationMessage> findBySessionIdOrderByCreatedAtAsc(@Param("sessionId") String sessionId);

    List<ConversationMessage> findBySessionIdAndRoleOrderByCreatedAtAsc(@Param("sessionId") String sessionId, @Param("role") String role);

    long countBySessionId(@Param("sessionId") String sessionId);

    void deleteBySessionId(@Param("sessionId") String sessionId);

    List<ConversationMessage> findHistoryPage(@Param("sessionId") String sessionId,
            @Param("beforeId") Long beforeId, @Param("limit") int limit);

    List<ConversationMessage> findRecentBySessionId(@Param("sessionId") String sessionId, @Param("pageable") Pageable pageable);

    Optional<ConversationMessage> findFirstBySessionIdAndRunIdAndRoleOrderByCreatedAtAsc(@Param("sessionId") String sessionId,
            @Param("runId") String runId,
            @Param("role") String role);
}
