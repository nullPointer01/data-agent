package com.ai.memory;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 会话级工作记忆，存储在 Redis 中。
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
     * 保存当前会话的任务状态。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param content 工作记忆内容
     */
    public void save(String tenantId, String userId, String sessionId, String content) {
        if (!hasIdentity(tenantId, userId) || !StringUtils.hasText(sessionId) || !StringUtils.hasText(content)) {
            return;
        }
        stringRedisTemplate.opsForValue().set(key(tenantId, userId, sessionId), content, WORKING_MEMORY_TTL);
    }

    /**
     * 获取工作记忆并刷新会话 TTL。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @return 工作记忆内容
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
     * 清除指定会话的工作记忆。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param sessionId 会话 ID
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
