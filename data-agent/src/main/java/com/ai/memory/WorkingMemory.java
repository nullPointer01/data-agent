package com.ai.memory;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Session-scoped working memory stored in Redis.
 *
 * @author data-agent
 */
@Service
public class WorkingMemory {

    private static final Duration WORKING_MEMORY_TTL = Duration.ofMinutes(35);
    private static final String KEY_PREFIX = "memory:working:";

    private final StringRedisTemplate stringRedisTemplate;

    public WorkingMemory(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Saves current task state for one session.
     *
     * @param tenantId tenant id
     * @param userId user id
     * @param sessionId session id
     * @param content working memory content
     */
    public void save(String tenantId, String userId, String sessionId, String content) {
        if (!hasIdentity(tenantId, userId) || !StringUtils.hasText(sessionId) || !StringUtils.hasText(content)) {
            return;
        }
        stringRedisTemplate.opsForValue().set(key(tenantId, userId, sessionId), content, WORKING_MEMORY_TTL);
    }

    /**
     * Gets working memory and refreshes its session TTL.
     *
     * @param tenantId tenant id
     * @param userId user id
     * @param sessionId session id
     * @return working memory content
     */
    public String get(String tenantId, String userId, String sessionId) {
        if (!hasIdentity(tenantId, userId) || !StringUtils.hasText(sessionId)) {
            return "";
        }
        String key = key(tenantId, userId, sessionId);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(value)) {
            stringRedisTemplate.expire(key, WORKING_MEMORY_TTL);
            return value;
        }
        return "";
    }

    /**
     * Clears working memory for one session.
     *
     * @param tenantId tenant id
     * @param userId user id
     * @param sessionId session id
     */
    public void clear(String tenantId, String userId, String sessionId) {
        if (!hasIdentity(tenantId, userId) || !StringUtils.hasText(sessionId)) {
            return;
        }
        stringRedisTemplate.delete(key(tenantId, userId, sessionId));
    }

    private String key(String tenantId, String userId, String sessionId) {
        return KEY_PREFIX + tenantId + ":" + userId + ":" + sessionId;
    }

    private boolean hasIdentity(String tenantId, String userId) {
        return StringUtils.hasText(tenantId) && StringUtils.hasText(userId);
    }
}
