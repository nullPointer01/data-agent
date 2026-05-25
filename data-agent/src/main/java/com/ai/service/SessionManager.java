package com.ai.service;

import com.ai.model.ConversationMessage;
import com.ai.model.ConversationSession;
import com.ai.model.ConversationSessionEntity;
import com.ai.repository.ConversationMessageRepository;
import com.ai.repository.ConversationSessionRepository;
import com.ai.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages tenant-scoped conversation session lifecycle and persistence.
 *
 * @author data-agent
 */
@Service
public class SessionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);
    private static final long SESSION_TTL_MINUTES = 35;
    private static final int MAX_HISTORY_MESSAGES = 50;
    private static final int TITLE_MAX_LENGTH = 50;
    private static final String REDIS_KEY_PREFIX = "session:";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private final ConversationSessionRepository sessionRepository;
    private final ConversationMessageRepository messageRepository;
    private final SecurityContextHelper securityContextHelper;
    private final RedisTemplate<String, ConversationSession> sessionRedisTemplate;

    public SessionManager(ConversationSessionRepository sessionRepository,
            ConversationMessageRepository messageRepository,
            SecurityContextHelper securityContextHelper,
            RedisTemplate<String, ConversationSession> sessionRedisTemplate) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.securityContextHelper = securityContextHelper;
        this.sessionRedisTemplate = sessionRedisTemplate;
    }

    private String redisKey(String sessionId) {
        return REDIS_KEY_PREFIX + sessionId;
    }

    /**
     * Creates a new session for current authenticated user.
     *
     * @return session id
     */
    @Transactional(rollbackFor = Exception.class)
    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        ConversationSession session = new ConversationSession(sessionId);
        sessionRedisTemplate.opsForValue().set(redisKey(sessionId), session, SESSION_TTL_MINUTES, TimeUnit.MINUTES);

        ConversationSessionEntity entity = new ConversationSessionEntity();
        entity.setSessionId(sessionId);
        entity.setUserId(securityContextHelper.getCurrentUserId());
        entity.setTenantId(securityContextHelper.getCurrentTenantId());
        entity.setStatus(STATUS_ACTIVE);
        sessionRepository.save(entity);

        LOGGER.debug("Session created: {}", sessionId);
        return sessionId;
    }

    /**
     * Gets one session after verifying current user and tenant ownership.
     *
     * @param sessionId session id
     * @return session or null when not found
     */
    @Transactional(readOnly = true)
    public ConversationSession getSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }

        Optional<ConversationSessionEntity> entityOptional = findCurrentUserSession(sessionId);
        if (entityOptional.isEmpty()) {
            return null;
        }

        ConversationSessionEntity entity = entityOptional.get();
        if (STATUS_CLOSED.equals(entity.getStatus())) {
            return null;
        }

        ConversationSession session = sessionRedisTemplate.opsForValue().get(redisKey(sessionId));
        if (session != null) {
            session.setLastAccessTime(new Date());
            sessionRedisTemplate.opsForValue().set(redisKey(sessionId), session, SESSION_TTL_MINUTES, TimeUnit.MINUTES);
            return session;
        }

        session = new ConversationSession(sessionId);
        List<ConversationMessage> messages = messageRepository.findRecentBySessionId(sessionId,
                PageRequest.of(0, MAX_HISTORY_MESSAGES));
        Collections.reverse(messages);
        replayMessages(session, messages);
        session.setModelId(entity.getModelId());
        session.setSkillId(entity.getSkillId());
        sessionRedisTemplate.opsForValue().set(redisKey(sessionId), session, SESSION_TTL_MINUTES, TimeUnit.MINUTES);

        return session;
    }

    /**
     * Destroys one current-user session.
     *
     * @param sessionId session id
     */
    @Transactional(rollbackFor = Exception.class)
    public void destroySession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        sessionRedisTemplate.delete(redisKey(sessionId));
        findCurrentUserSession(sessionId).ifPresent(entity -> {
            entity.setStatus(STATUS_CLOSED);
            sessionRepository.save(entity);
        });
        LOGGER.debug("Session destroyed: {}", sessionId);
    }

    /**
     * Saves one conversation message and refreshes session metadata.
     *
     * @param sessionId session id
     * @param role message role
     * @param content message content
     * @param skillUsed skill id
     * @param modelUsed model id
     * @param tokens token count
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMessage(String sessionId, String role, String content, String skillUsed, String modelUsed,
            long tokens) {
        Optional<ConversationSessionEntity> entityOptional = findCurrentUserSession(sessionId);
        if (entityOptional.isEmpty()) {
            LOGGER.warn("Skip saving message for unauthorized or missing session: {}", sessionId);
            return;
        }

        ConversationMessage message = new ConversationMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSkillUsed(skillUsed);
        message.setModelUsed(modelUsed);
        message.setTokens(tokens);
        messageRepository.save(message);

        updateSessionMetadata(entityOptional.get(), role, content, skillUsed, modelUsed, tokens);

        ConversationSession session = sessionRedisTemplate.opsForValue().get(redisKey(sessionId));
        if (session != null) {
            appendSessionMessage(session, role, content);
            sessionRedisTemplate.opsForValue().set(redisKey(sessionId), session, SESSION_TTL_MINUTES, TimeUnit.MINUTES);
        }
    }

    public List<ConversationSessionEntity> getUserSessions(String userId) {
        return sessionRepository.findByUserIdOrderByLastAccessAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<ConversationMessage> getSessionMessages(String sessionId) {
        if (findCurrentUserSession(sessionId).isEmpty()) {
            return List.of();
        }
        List<ConversationMessage> messages = messageRepository.findRecentBySessionId(sessionId,
                PageRequest.of(0, MAX_HISTORY_MESSAGES));
        Collections.reverse(messages);
        return messages;
    }


    private Optional<ConversationSessionEntity> findCurrentUserSession(String sessionId) {
        String userId = securityContextHelper.getCurrentUserId();
        String tenantId = securityContextHelper.getCurrentTenantId();
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(userId) || !StringUtils.hasText(tenantId)) {
            return Optional.empty();
        }
        return sessionRepository.findBySessionIdAndUserIdAndTenantId(sessionId, userId, tenantId);
    }

    private void replayMessages(ConversationSession session, List<ConversationMessage> messages) {
        for (ConversationMessage message : messages) {
            appendSessionMessage(session, message.getRole(), message.getContent());
        }
    }

    private void appendSessionMessage(ConversationSession session, String role, String content) {
        if (ROLE_USER.equals(role)) {
            session.addUserMessage(content);
        } else if (ROLE_ASSISTANT.equals(role)) {
            session.addAssistantMessage(content);
        }
    }

    private void updateSessionMetadata(ConversationSessionEntity entity, String role, String content,
            String skillUsed, String modelUsed, long tokens) {
        entity.setMessageCount(entity.getMessageCount() + 1);
        entity.setTotalTokens(entity.getTotalTokens() + tokens);
        if (entity.getTitle() == null && ROLE_USER.equals(role) && StringUtils.hasText(content)) {
            entity.setTitle(buildTitle(content));
        }
        if (skillUsed != null) {
            entity.setSkillId(skillUsed);
        }
        if (modelUsed != null) {
            entity.setModelId(modelUsed);
        }
        sessionRepository.save(entity);
    }

    private String buildTitle(String content) {
        if (content.length() <= TITLE_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, TITLE_MAX_LENGTH) + "...";
    }
}
