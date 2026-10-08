package com.ai.repository;

import com.ai.model.ConversationSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 持久化对话会话仓储。
 *
 * @author data-agent
 */
public interface ConversationSessionRepository extends JpaRepository<ConversationSessionEntity, String> {

    /** 保存一轮消息时锁定所属会话，串行更新计数并保证同一 Run 重放不重复追加。 */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM ConversationSessionEntity s WHERE s.sessionId = :sessionId AND s.userId = :userId AND s.tenantId = :tenantId")
    Optional<ConversationSessionEntity> findOwnedForUpdate(
            @org.springframework.data.repository.query.Param("sessionId") String sessionId,
            @org.springframework.data.repository.query.Param("userId") String userId,
            @org.springframework.data.repository.query.Param("tenantId") String tenantId);

    /**
     * 查询某个用户拥有的会话。
     *
     * @param userId 用户 ID
     * @return 会话实体列表
     */
    List<ConversationSessionEntity> findByUserIdOrderByLastAccessAtDesc(String userId);

    /**
     * 按用户和状态查询会话，按最后访问时间倒序。
     */
    List<ConversationSessionEntity> findByUserIdAndStatusOrderByLastAccessAtDesc(String userId, String status);

    /**
     * 查询某个租户拥有的会话。
     *
     * @param tenantId 租户 ID
     * @return 会话实体列表
     */
    List<ConversationSessionEntity> findByTenantIdOrderByLastAccessAtDesc(String tenantId);

    /**
     * 按状态查询用户会话。
     *
     * @param userId 用户 ID
     * @param status 会话状态
     * @return 会话实体列表
     */
    List<ConversationSessionEntity> findByUserIdAndStatus(String userId, String status);

    /**
     * 查询某个用户拥有的一个会话。
     *
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @return 匹配的会话
     */
    Optional<ConversationSessionEntity> findBySessionIdAndUserId(String sessionId, String userId);

    /**
     * 查询某个用户在某个租户中的一个会话。
     *
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 匹配的会话
     */
    Optional<ConversationSessionEntity> findBySessionIdAndUserIdAndTenantId(String sessionId, String userId,
            String tenantId);

    /**
     * 统计某个用户拥有的会话数。
     *
     * @param userId 用户 ID
     * @return 会话数
     */
    long countByUserId(String userId);

}
