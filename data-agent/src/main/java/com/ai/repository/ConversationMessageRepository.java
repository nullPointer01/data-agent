package com.ai.repository;

import com.ai.model.ConversationMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for persisted conversation messages.
 *
 * @author data-agent
 */
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    /**
     * Finds all messages in one session.
     *
     * @param sessionId session id
     * @return messages
     */
    List<ConversationMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * Finds messages in one session by role.
     *
     * @param sessionId session id
     * @param role message role
     * @return messages
     */
    List<ConversationMessage> findBySessionIdAndRoleOrderByCreatedAtAsc(String sessionId, String role);

    /**
     * Counts messages in one session.
     *
     * @param sessionId session id
     * @return message count
     */
    long countBySessionId(String sessionId);

    /**
     * Deletes messages in one session.
     *
     * @param sessionId session id
     */
    void deleteBySessionId(String sessionId);

    /**
     * Finds recent messages in reverse chronological order.
     *
     * @param sessionId session id
     * @param pageable page request
     * @return recent messages
     */
    @Query("SELECT m FROM ConversationMessage m WHERE m.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<ConversationMessage> findRecentBySessionId(@Param("sessionId") String sessionId, Pageable pageable);
}
