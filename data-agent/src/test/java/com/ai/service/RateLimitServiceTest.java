package com.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitServiceTest {

    @Test
    void fallsBackToLocalCountersWhenRedisIsUnavailable() {
        RateLimitService service = new RateLimitService(new EmptyStringRedisTemplateProvider());

        assertTrue(service.checkUserMinuteLimit("user-1", 2).allowed());
        assertTrue(service.checkUserMinuteLimit("user-1", 2).allowed());
        assertFalse(service.checkUserMinuteLimit("user-1", 2).allowed());
    }

    private static class EmptyStringRedisTemplateProvider implements ObjectProvider<StringRedisTemplate> {
        @Override
        public StringRedisTemplate getObject(Object... args) {
            return null;
        }

        @Override
        public StringRedisTemplate getIfAvailable() {
            return null;
        }

        @Override
        public StringRedisTemplate getIfUnique() {
            return null;
        }

        @Override
        public StringRedisTemplate getObject() {
            return null;
        }
    }
}
