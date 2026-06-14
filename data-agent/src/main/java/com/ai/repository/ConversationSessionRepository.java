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

    /**
     * 查询某个用户拥有的会话。
     *
     * @param userId 用户 ID
     * @return 会话实体列表
     */
    List<ConversationSessionEntity> findByUserIdOrderByLastAccessAtDesc(String userId);

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
