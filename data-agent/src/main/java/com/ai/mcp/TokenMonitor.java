package com.ai.mcp;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内存令牌使用监控和轻量级令牌估算器。
 *
 * @author data-agent
 */
@Service
public class TokenMonitor {

    private static final Pattern LATIN_WORD_PATTERN = Pattern.compile("[a-zA-Z]+");
    private static final double CJK_TOKEN_WEIGHT = 1.0D;
    private static final double WORD_TOKEN_WEIGHT = 1.3D;
    private static final double OTHER_TOKEN_WEIGHT = 0.25D;

    private final Map<String, AtomicLong> skillTokenUsage = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> modelTokenUsage = new ConcurrentHashMap<>();

    public void recordSkillTokenUsage(String skillId, long tokens) {
        skillTokenUsage.computeIfAbsent(skillId, k -> new AtomicLong(0)).addAndGet(tokens);
    }

    public void recordModelTokenUsage(String modelType, long tokens) {
        modelTokenUsage.computeIfAbsent(modelType, k -> new AtomicLong(0)).addAndGet(tokens);
    }

    public Map<String, Long> getSkillTokenUsage() {
        Map<String, Long> result = new LinkedHashMap<>();
        skillTokenUsage.forEach((skillId, usage) -> result.put(skillId, usage.get()));
        return result;
    }

    public Map<String, Long> getModelTokenUsage() {
        Map<String, Long> result = new LinkedHashMap<>();
        modelTokenUsage.forEach((modelType, usage) -> result.put(modelType, usage.get()));
        return result;
    }

    public long estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int cjkCount = 0;
        int otherCount = 0;
        int wordCount = 0;

        Matcher wordMatcher = LATIN_WORD_PATTERN.matcher(text);
        while (wordMatcher.find()) {
            wordCount++;
        }

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjkChar(c)) {
                cjkCount++;
            } else if (!Character.isLetter(c)) {
                otherCount++;
            }
        }

        return Math.round(cjkCount * CJK_TOKEN_WEIGHT + wordCount * WORD_TOKEN_WEIGHT
                + otherCount * OTHER_TOKEN_WEIGHT);
    }

    private boolean isCjkChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO;
    }

    public void resetTokenUsage() {
        skillTokenUsage.clear();
        modelTokenUsage.clear();
    }
}
