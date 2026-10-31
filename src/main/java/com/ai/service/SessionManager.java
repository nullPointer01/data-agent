package com.ai.service;

import com.ai.memory.ConversationMemoryCaptureService;
import com.ai.memory.MemoryCaptureMode;
import com.ai.conversation.ConversationAccessGuard;
import com.ai.conversation.dto.SessionMessagesResponse;

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
    private final ConversationMemoryCaptureService memoryCapture;
    private final ConversationAccessGuard accessGuard;
    private final com.ai.agent.backgroundrun.BackgroundRunMapper backgroundRuns;

    public SessionManager(ConversationSessionRepository sessionRepository,
            ConversationMessageRepository messageRepository,
            SecurityContextHelper securityContextHelper,
            RedisTemplate<String, ConversationSession> sessionRedisTemplate,
            ConversationMemoryCaptureService memoryCapture, ConversationAccessGuard accessGuard,
            com.ai.agent.backgroundrun.BackgroundRunMapper backgroundRuns) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.securityContextHelper = securityContextHelper;
        this.sessionRedisTemplate = sessionRedisTemplate;
        this.memoryCapture = memoryCapture;
        this.accessGuard = accessGuard;
        this.backgroundRuns = backgroundRuns;
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
        if (!STATUS_ACTIVE.equals(entity.getStatus())) {
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
        ConversationSessionEntity entity = sessionRepository.findOwnedForUpdate(sessionId,
                        securityContextHelper.getCurrentUserId(), securityContextHelper.getCurrentTenantId())
                .orElseThrow(() -> new IllegalArgumentException("会话不存在或无权限"));
        if (!STATUS_ACTIVE.equals(entity.getStatus()) && !"ARCHIVED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("会话已删除，不能重命名");
        }
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
            String runId, MemoryCaptureMode captureMode, long memoryRevision) {
        Optional<ConversationSessionEntity> entityOptional = sessionRepository.findOwnedForUpdate(sessionId,
                securityContextHelper.getCurrentUserId(), securityContextHelper.getCurrentTenantId());
        if (entityOptional.isEmpty()) {
            throw new IllegalStateException("会话不存在或无权限，回答未保存");
        }
        ConversationSessionEntity entity = entityOptional.get();
        if (!STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new IllegalStateException("会话已删除或归档，回答未保存");
        }
        if (StringUtils.hasText(runId) && findRunAssistantMessage(sessionId, runId).isPresent()) {
            var current = com.ai.agent.runtime.AgentRunScope.current().orElse(null);
            boolean background = backgroundRuns.find(runId).isPresent();
            if (!background) return false;
            String status = backgroundRuns.lockRun(runId).orElseThrow();
            // 正常完成必须仍拥有 RUNNING 租约；审批挂起的更新与原审批事务共享 Run 锁。
            if (current == null || (!"RUNNING".equals(status) && !"WAITING_APPROVAL".equals(status))) {
                throw new IllegalStateException("Run 已不可写入回答");
            }
            if ("RUNNING".equals(status)) current.control().ensureActiveForWrite();
            updateRunAssistantMessage(sessionId, entity.getTenantId(), entity.getUserId(), runId,
                    assistantContent, skillUsed, modelUsed, assistantTokens);
            var source = messageRepository.findFirstBySessionIdAndRunIdAndRoleOrderByCreatedAtAsc(sessionId, runId, ROLE_USER)
                    .orElseThrow(() -> new IllegalStateException("后台任务用户消息缺失"));
            long previousUserTokens = source.getTokens();
            source.setTokens(userTokens);
            messageRepository.save(source);
            var updatedSession = sessionRepository.findOwnedForUpdate(sessionId, entity.getUserId(), entity.getTenantId()).orElseThrow();
            updatedSession.setTotalTokens(Math.max(0, updatedSession.getTotalTokens() - previousUserTokens + userTokens));
            sessionRepository.save(updatedSession);
            memoryCapture.captureCompletedConversation(sessionId, userContent, assistantContent, modelUsed,
                    captureMode, source.getId(), memoryRevision);
            return true;
        }
        ConversationMessage userMessage = buildMessage(
                sessionId, ROLE_USER, userContent, null, null, userTokens, runId);
        ConversationMessage assistantMessage = buildMessage(
                sessionId, ROLE_ASSISTANT, assistantContent, skillUsed, modelUsed, assistantTokens, runId);
        messageRepository.save(userMessage);
        messageRepository.save(assistantMessage);
        updateSessionMetadata(entity, ROLE_USER, userContent, null, null, userTokens);
        updateSessionMetadata(entity, ROLE_ASSISTANT, assistantContent, skillUsed, modelUsed, assistantTokens);
        // 用户消息 ID 与提取任务同事务落库，进程退出不会丢失已提交的后台工作。
        // 持久提交只是排队占位，不能把“等待执行”提炼为会话摘要或长期事实。
        if (!StringUtils.hasText(runId) || backgroundRuns.find(runId).isEmpty()
                || com.ai.agent.runtime.AgentRunScope.current().isPresent()) {
            memoryCapture.captureCompletedConversation(sessionId, userContent, assistantContent, modelUsed,
                    captureMode, userMessage.getId(), memoryRevision);
        }
        invalidateCacheAfterCommit(sessionId);
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
                .findOwnedForUpdate(sessionId, userId, tenantId);
        Optional<ConversationMessage> messageOptional = findRunAssistantMessage(sessionId, runId);
        if (entityOptional.isEmpty() || messageOptional.isEmpty()) {
            LOGGER.warn("未找到待更新的 Run 会话消息: sessionId={}, runId={}", sessionId, runId);
            return false;
        }

        ConversationSessionEntity entity = entityOptional.get();
        if (!STATUS_ACTIVE.equals(entity.getStatus())) {
            return false;
        }
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
        invalidateCacheAfterCommit(sessionId);
        return true;
    }

    private void invalidateCacheAfterCommit(String sessionId) {
        // 主库和 Outbox 提交后再失效缓存，回滚时不能留下看似保存成功的缓存消息。
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() {
                        try { sessionRedisTemplate.delete(redisKey(sessionId)); }
                        catch (RuntimeException error) { LOGGER.warn("会话提交后缓存失效失败: sessionId={}",sessionId); }
                    }
                });
    }

    /** 用户历史使用 ID 游标加载，与模型最近 50 条上下文窗口独立。 */
    @Transactional(readOnly = true)
    public SessionMessagesResponse getSessionMessages(String sessionId, Long beforeId, int limit) {
        var session = accessGuard.requireReadable(sessionId, securityContextHelper.getCurrentTenantId(),
                securityContextHelper.getCurrentUserId());
        if (beforeId != null && beforeId <= 0) {
            throw new IllegalArgumentException("消息游标无效");
        }
        int size = Math.max(1, Math.min(100, limit));
        List<ConversationMessage> messages = new java.util.ArrayList<>(
                messageRepository.findHistoryPage(sessionId, beforeId, size + 1));
        boolean more = messages.size() > size;
        if (more) messages.remove(messages.size() - 1);
        Long next = more ? messages.get(messages.size() - 1).getId() : null;
        Collections.reverse(messages);
        return SessionMessagesResponse.ok(messages, next, session.getStatus());
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
