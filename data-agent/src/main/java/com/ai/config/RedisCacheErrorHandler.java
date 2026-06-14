package com.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Redis 缓存容错处理器。
 * <p>
 * 当 Redis 不可用时（连接超时、宕机等），降级为直接走 DB 查询，
 * 不让缓存异常打断正常业务流程。
 *
 * @author data-agent
 */
public class RedisCacheErrorHandler implements CacheErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        LOGGER.warn("Redis 缓存读取失败，降级走 DB. cache={}, key={}, error={}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        LOGGER.warn("Redis 缓存写入失败，跳过. cache={}, key={}, error={}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        LOGGER.warn("Redis 缓存驱逐失败，跳过. cache={}, key={}, error={}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        LOGGER.warn("Redis 缓存清空失败，跳过. cache={}, error={}",
                cache.getName(), exception.getMessage());
    }
}
