package com.ai.repository;

import com.ai.model.ConversationSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for persisted conversation sessions.
 *
 * @author data-agent
 */
public interface ConversationSessionRepository extends JpaRepository<ConversationSessionEntity, String> {

    /**
     * Finds sessions owned by one user.
     *
     * @param userId user id
     * @return session entities
     */
    List<ConversationSessionEntity> findByUserIdOrderByLastAccessAtDesc(String userId);

    /**
     * Finds sessions owned by one tenant.
     *
     * @param tenantId tenant id
     * @return session entities
     */
    List<ConversationSessionEntity> findByTenantIdOrderByLastAccessAtDesc(String tenantId);

    /**
     * Finds user sessions by status.
     *
     * @param userId user id
     * @param status session status
     * @return session entities
     */
    List<ConversationSessionEntity> findByUserIdAndStatus(String userId, String status);

    /**
     * Finds one session owned by one user.
     *
     * @param sessionId session id
     * @param userId user id
     * @return matched session
     */
    Optional<ConversationSessionEntity> findBySessionIdAndUserId(String sessionId, String userId);

    /**
     * Finds one session owned by one user in one tenant.
     *
     * @param sessionId session id
     * @param userId user id
     * @param tenantId tenant id
     * @return matched session
     */
    Optional<ConversationSessionEntity> findBySessionIdAndUserIdAndTenantId(String sessionId, String userId,
            String tenantId);

    /**
     * Counts sessions owned by one user.
     *
     * @param userId user id
     * @return session count
     */
    long countByUserId(String userId);

}
