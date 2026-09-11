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
import java.util.regex.Pattern;

/**
 * 租户维度的对话会话生命周期管理与持久化服务。
 *
 * @author data-agent
 */
@Service
public class SessionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);
    private static final long SESSION_TTL_MINUTES = 35;
    private static final int MAX_HISTORY_MESSAGES = 50;
    private static final int AUTO_TITLE_MAX_LENGTH = 28;
    private static final int MANUAL_TITLE_MAX_LENGTH = 80;
    private static final String REDIS_KEY_PREFIX = "session:";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String SENTENCE_END_MARKS = "。！？!?；;";
    private static final Pattern TITLE_WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern TITLE_LEADING_FILLER_PATTERN = Pattern.compile(
            "^(请问|请帮我|帮我|麻烦你?|我想要?|我需要|能否|是否可以|可以帮我)[，,:：\\s]*");
    private static final Pattern TITLE_TRAILING_PUNCTUATION_PATTERN = Pattern.compile("[。！？!?；;，,：:\\s]+$");

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
     * 为当前认证用户创建新会话。
     *
     * @return 会话编号
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
     * 校验当前用户和租户归属后获取会话。
     *
     * @param sessionId 会话编号
     * @return 会话对象，未找到时返回 null
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
     * 销毁当前用户的一个会话。
     *
     * @param sessionId 会话编号
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
     * 修改当前用户拥有的会话标题。
     *
     * @param sessionId 会话编号
     * @param title 新标题
     */
    @Transactional(rollbackFor = Exception.class)
    public void renameSession(String sessionId, String title) {
        String normalizedTitle = normalizeTitle(title);
        if (!StringUtils.hasText(normalizedTitle)) {
            throw new IllegalArgumentException("会话标题不能为空");
        }
        if (normalizedTitle.length() > MANUAL_TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("会话标题不能超过80个字符");
        }
        ConversationSessionEntity entity = findCurrentUserSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在或无权限"));
        entity.setTitle(normalizedTitle);
        sessionRepository.save(entity);
    }

    /**
     * 原子保存一次用户请求及其助手消息，同一 Run 重复提交时不重复追加。
     *
     * @param sessionId 会话编号
     * @param userContent 用户消息
     * @param assistantContent 助手消息
     * @param skillUsed 技能编号
     * @param modelUsed 模型编号
     * @param userTokens 用户消息 Token
     * @param assistantTokens 助手消息 Token
     * @param runId Agent Run 编号
     * @return 是否新增了这轮对话
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean saveConversationTurn(String sessionId,
            String userContent,
            String assistantContent,
            String skillUsed,
            String modelUsed,
            long userTokens,
            long assistantTokens,
            String runId) {
        Optional<ConversationSessionEntity> entityOptional = findCurrentUserSession(sessionId);
        if (entityOptional.isEmpty()) {
            LOGGER.warn("跳过保存对话，会话不存在或无权限: {}", sessionId);
            return false;
        }
        if (StringUtils.hasText(runId) && findRunAssistantMessage(sessionId, runId).isPresent()) {
            return false;
        }

        ConversationSessionEntity entity = entityOptional.get();
        ConversationMessage userMessage = buildMessage(
                sessionId, ROLE_USER, userContent, null, null, userTokens, null);
        ConversationMessage assistantMessage = buildMessage(
                sessionId, ROLE_ASSISTANT, assistantContent, skillUsed, modelUsed, assistantTokens, runId);
        messageRepository.save(userMessage);
        messageRepository.save(assistantMessage);
        updateSessionMetadata(entity, ROLE_USER, userContent, null, null, userTokens);
        updateSessionMetadata(entity, ROLE_ASSISTANT, assistantContent, skillUsed, modelUsed, assistantTokens);

        ConversationSession session = sessionRedisTemplate.opsForValue().get(redisKey(sessionId));
        if (session != null) {
            appendSessionMessage(session, ROLE_USER, userContent);
            appendSessionMessage(session, ROLE_ASSISTANT, assistantContent);
            sessionRedisTemplate.opsForValue().set(redisKey(sessionId), session,
                    SESSION_TTL_MINUTES, TimeUnit.MINUTES);
        }
        return true;
    }

    /**
     * 使用持久化 Run 的所有者信息更新已保存的助手消息。
     *
     * <p>该入口供无登录上下文的恢复任务和审批调度使用，必须同时匹配会话、租户和用户。</p>
     *
     * @param sessionId 会话编号
     * @param tenantId 租户编号
     * @param userId 会话所有者编号
     * @param runId Agent Run 编号
     * @param content 新的助手消息
     * @param skillUsed 技能编号，为空时保留原值
     * @param modelUsed 模型编号，为空时保留原值
     * @param tokens 新消息 Token
     * @return 是否更新成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRunAssistantMessage(String sessionId,
            String tenantId,
            String userId,
            String runId,
            String content,
            String skillUsed,
            String modelUsed,
            long tokens) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(tenantId)
                || !StringUtils.hasText(userId) || !StringUtils.hasText(runId)) {
            return false;
        }
        Optional<ConversationSessionEntity> entityOptional = sessionRepository
                .findBySessionIdAndUserIdAndTenantId(sessionId, userId, tenantId);
        Optional<ConversationMessage> messageOptional = findRunAssistantMessage(sessionId, runId);
        if (entityOptional.isEmpty() || messageOptional.isEmpty()) {
            LOGGER.warn("未找到待更新的 Run 会话消息: sessionId={}, runId={}", sessionId, runId);
            return false;
        }

        ConversationSessionEntity entity = entityOptional.get();
        ConversationMessage message = messageOptional.get();
        long previousTokens = message.getTokens();
        message.setContent(content);
        message.setTokens(tokens);
        if (StringUtils.hasText(skillUsed)) {
            message.setSkillUsed(skillUsed);
        }
        if (StringUtils.hasText(modelUsed)) {
            message.setModelUsed(modelUsed);
        }
        messageRepository.save(message);

        entity.setTotalTokens(Math.max(0L, entity.getTotalTokens() - previousTokens + tokens));
        if (StringUtils.hasText(skillUsed)) {
            entity.setSkillId(skillUsed);
        }
        if (StringUtils.hasText(modelUsed)) {
            entity.setModelId(modelUsed);
        }
        sessionRepository.save(entity);
        sessionRedisTemplate.delete(redisKey(sessionId));
        return true;
    }

    public List<ConversationSessionEntity> getUserSessions(String userId) {
        return sessionRepository.findByUserIdAndStatusOrderByLastAccessAtDesc(userId, STATUS_ACTIVE);
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

    private Optional<ConversationMessage> findRunAssistantMessage(String sessionId, String runId) {
        return messageRepository.findFirstBySessionIdAndRunIdAndRoleOrderByCreatedAtAsc(
                sessionId, runId, ROLE_ASSISTANT);
    }

    private ConversationMessage buildMessage(String sessionId,
            String role,
            String content,
            String skillUsed,
            String modelUsed,
            long tokens,
            String runId) {
        ConversationMessage message = new ConversationMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSkillUsed(skillUsed);
        message.setModelUsed(modelUsed);
        message.setTokens(tokens);
        message.setRunId(runId);
        return message;
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
        if (!StringUtils.hasText(entity.getTitle()) && ROLE_USER.equals(role) && StringUtils.hasText(content)) {
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
        String originalTitle = normalizeTitle(content);
        String normalized = TITLE_LEADING_FILLER_PATTERN.matcher(originalTitle).replaceFirst("");
        if (!StringUtils.hasText(normalized)) {
            normalized = originalTitle;
        }
        int sentenceEnd = firstSentenceEnd(normalized);
        String summary = sentenceEnd >= 0 ? normalized.substring(0, sentenceEnd) : normalized;
        summary = TITLE_TRAILING_PUNCTUATION_PATTERN.matcher(summary).replaceFirst("");
        if (!StringUtils.hasText(summary)) {
            summary = normalized;
        }
        return abbreviate(summary, AUTO_TITLE_MAX_LENGTH);
    }

    private String normalizeTitle(String title) {
        return title == null ? "" : TITLE_WHITESPACE_PATTERN.matcher(title.trim()).replaceAll(" ");
    }

    private int firstSentenceEnd(String content) {
        for (int index = 0; index < content.length(); index++) {
            if (SENTENCE_END_MARKS.indexOf(content.charAt(index)) >= 0) {
                return index;
            }
        }
        return -1;
    }

    private String abbreviate(String content, int maxLength) {
        int codePointCount = content.codePointCount(0, content.length());
        if (codePointCount <= maxLength) {
            return content;
        }
        int endIndex = content.offsetByCodePoints(0, maxLength - 3);
        return content.substring(0, endIndex) + "...";
    }
}
