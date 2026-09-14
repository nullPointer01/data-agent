package com.ai.mcp;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在供应商未返回精确用量时使用的轻量级令牌估算器。
 *
 * @author data-agent
 */
@Service
public class TokenMonitor {

    private static final Pattern LATIN_WORD_PATTERN = Pattern.compile("[a-zA-Z]+");
    private static final double CJK_TOKEN_WEIGHT = 1.0D;
    private static final double WORD_TOKEN_WEIGHT = 1.3D;
    private static final double OTHER_TOKEN_WEIGHT = 0.25D;

    /**
     * 按中日韩字符、拉丁单词和其他字符估算 Token 数量。
     *
     * @param text 待估算文本
     * @return 近似 Token 数量
     */
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

}
