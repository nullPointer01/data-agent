package com.ai.repository;

import com.ai.model.ConversationSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationSessionRepository extends JpaRepository<ConversationSessionEntity, String> {
    List<ConversationSessionEntity> findByUserIdOrderByLastAccessAtDesc(String userId);
    List<ConversationSessionEntity> findByTenantIdOrderByLastAccessAtDesc(String tenantId);
    List<ConversationSessionEntity> findByUserIdAndStatus(String userId, String status);
    Optional<ConversationSessionEntity> findBySessionIdAndUserId(String sessionId, String userId);
    long countByUserId(String userId);
}
