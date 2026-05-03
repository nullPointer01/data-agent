package com.ai.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TokenMonitor {

    private static final Logger log = LoggerFactory.getLogger(TokenMonitor.class);

    private final Map<String, AtomicLong> skillTokenUsage = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> modelTokenUsage = new ConcurrentHashMap<>();

    public void recordSkillTokenUsage(String skillId, long tokens) {
        skillTokenUsage.computeIfAbsent(skillId, k -> new AtomicLong(0)).addAndGet(tokens);
    }

    public void recordModelTokenUsage(String modelType, long tokens) {
        modelTokenUsage.computeIfAbsent(modelType, k -> new AtomicLong(0)).addAndGet(tokens);
    }

    public Map<String, Long> getSkillTokenUsage() {
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        skillTokenUsage.forEach((skillId, usage) -> result.put(skillId, usage.get()));
        return result;
    }

    public Map<String, Long> getModelTokenUsage() {
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        modelTokenUsage.forEach((modelType, usage) -> result.put(modelType, usage.get()));
        return result;
    }

    public long estimateTokens(String text) {
        if (text == null) return 0;
        String[] words = text.split("\\s+");
        int chineseChars = text.replaceAll("[\\x00-\\xff]", "").length();
        return (long) (words.length * 1.3 + chineseChars * 2);
    }

    public void resetTokenUsage() {
        skillTokenUsage.clear();
        modelTokenUsage.clear();
    }
}
