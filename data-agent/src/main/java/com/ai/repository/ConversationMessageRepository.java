package com.ai.repository;

import com.ai.model.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    List<ConversationMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    List<ConversationMessage> findBySessionIdAndRoleOrderByCreatedAtAsc(String sessionId, String role);
    long countBySessionId(String sessionId);
    void deleteBySessionId(String sessionId);
}
