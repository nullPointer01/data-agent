package com.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Redis 的限流服务，Redis 不可用时回退到本地内存计数。
 *
 * @author data-agent
 */
@Service
public class RateLimitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitService.class);
    private static final long REDIS_FALLBACK_MARKER = -1L;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long COUNTER_TTL_SECONDS = 120L;
    private static final long LOCAL_COUNTER_KEEP_MINUTES = 2L;
    private static final String KEY_PREFIX_USER_RATE_LIMIT = "rl:user:";
    private static final String KEY_PART_DELIMITER = ":";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<String, AtomicLong> localCounters = new ConcurrentHashMap<>();

    public RateLimitService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    /**
     * 检查单个用户的每分钟请求限额。
     *
     * @param userId 用户编号
     * @param requestsPerMinute 每分钟请求上限
     * @return 限流检查结果
     */
    public RateLimitResult checkUserMinuteLimit(String userId, int requestsPerMinute) {
        long epochMinute = System.currentTimeMillis() / MILLIS_PER_MINUTE;
        String key = KEY_PREFIX_USER_RATE_LIMIT + userId + KEY_PART_DELIMITER + epochMinute;
        long count = incrementWithRedis(key);
        if (count == REDIS_FALLBACK_MARKER) {
            count = incrementLocally(key, epochMinute);
        }
        return new RateLimitResult(count <= requestsPerMinute, count, requestsPerMinute);
    }

    private long incrementWithRedis(String key) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return REDIS_FALLBACK_MARKER;
        }
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, COUNTER_TTL_SECONDS, TimeUnit.SECONDS);
            }
            return count != null ? count : REDIS_FALLBACK_MARKER;
        } catch (Exception e) {
            LOGGER.warn("Redis 限流检查失败，回退到本地计数: {}", e.getMessage());
            return REDIS_FALLBACK_MARKER;
        }
    }

    private long incrementLocally(String key, long epochMinute) {
        cleanupOldLocalCounters(epochMinute);
        return localCounters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    private void cleanupOldLocalCounters(long epochMinute) {
        long oldestAllowedMinute = epochMinute - LOCAL_COUNTER_KEEP_MINUTES;
        String oldestAllowedSuffix = KEY_PART_DELIMITER + oldestAllowedMinute;
        Iterator<String> iterator = localCounters.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            int lastColon = key.lastIndexOf(KEY_PART_DELIMITER);
            if (lastColon < 0) {
                continue;
            }
            try {
                long keyMinute = Long.parseLong(key.substring(lastColon + 1));
                if (keyMinute < oldestAllowedMinute) {
                    iterator.remove();
                }
            } catch (NumberFormatException e) {
                if (!key.endsWith(oldestAllowedSuffix)) {
                    iterator.remove();
                }
            }
        }
    }

    public record RateLimitResult(boolean allowed, long count, int limit) {
    }
}
