package com.ai.service;

import com.ai.model.ConversationMessage;
import com.ai.model.ConversationSession;
import com.ai.model.ConversationSessionEntity;
import com.ai.repository.ConversationMessageRepository;
import com.ai.repository.ConversationSessionRepository;
import com.ai.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000;
    private final Map<String, ConversationSession> sessions = new ConcurrentHashMap<>();

    private final ConversationSessionRepository sessionRepository;
    private final ConversationMessageRepository messageRepository;
    private final SecurityContextHelper securityContextHelper;

    public SessionManager(ConversationSessionRepository sessionRepository,
            ConversationMessageRepository messageRepository,
            SecurityContextHelper securityContextHelper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.securityContextHelper = securityContextHelper;
    }

    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        ConversationSession session = new ConversationSession(sessionId);
        sessions.put(sessionId, session);

        ConversationSessionEntity entity = new ConversationSessionEntity();
        entity.setSessionId(sessionId);
        entity.setUserId(securityContextHelper.getCurrentUserId());
        entity.setTenantId(securityContextHelper.getCurrentTenantId());
        entity.setStatus("ACTIVE");
        sessionRepository.save(entity);

        log.debug("Session created: {}", sessionId);
        return sessionId;
    }

    public ConversationSession getSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty())
            return null;
        ConversationSession session = sessions.get(sessionId);
        if (session != null) {
            session.setLastAccessTime(new Date());
            return session;
        }

        ConversationSessionEntity entity = sessionRepository.findById(sessionId).orElse(null);
        if (entity == null || "CLOSED".equals(entity.getStatus()))
            return null;

        session = new ConversationSession(sessionId);
        List<ConversationMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        for (ConversationMessage msg : messages) {
            if ("user".equals(msg.getRole())) {
                session.addUserMessage(msg.getContent());
            } else if ("assistant".equals(msg.getRole())) {
                session.addAssistantMessage(msg.getContent());
            }
        }
        session.setModelId(entity.getModelId());
        session.setSkillId(entity.getSkillId());
        sessions.put(sessionId, session);

        return session;
    }

    public void destroySession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
            sessionRepository.findById(sessionId).ifPresent(entity -> {
                entity.setStatus("CLOSED");
                sessionRepository.save(entity);
            });
            log.debug("Session destroyed: {}", sessionId);
        }
    }

    @Transactional
    public void saveMessage(String sessionId, String role, String content, String skillUsed, String modelUsed,
            long tokens) {
        ConversationMessage message = new ConversationMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSkillUsed(skillUsed);
        message.setModelUsed(modelUsed);
        message.setTokens(tokens);
        messageRepository.save(message);

        sessionRepository.findById(sessionId).ifPresent(entity -> {
            entity.setMessageCount(entity.getMessageCount() + 1);
            entity.setTotalTokens(entity.getTotalTokens() + tokens);
            if (entity.getTitle() == null && "user".equals(role) && content != null && !content.isEmpty()) {
                entity.setTitle(content.length() > 50 ? content.substring(0, 50) + "..." : content);
            }
            if (skillUsed != null)
                entity.setSkillId(skillUsed);
            if (modelUsed != null)
                entity.setModelId(modelUsed);
            sessionRepository.save(entity);
        });
    }

    public List<ConversationSessionEntity> getUserSessions(String userId) {
        return sessionRepository.findByUserIdOrderByLastAccessAtDesc(userId);
    }

    public List<ConversationMessage> getSessionMessages(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            long lastAccess = entry.getValue().getLastAccessTime().getTime();
            boolean expired = (now - lastAccess) > SESSION_TIMEOUT_MS;
            if (expired)
                log.debug("Session expired: {}", entry.getKey());
            return expired;
        });
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}
