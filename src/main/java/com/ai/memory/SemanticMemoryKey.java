package com.ai.memory;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 语义记忆稳定键约定，统一提取、去重、手工修正和画像投影的键空间。
 *
 * @author data-agent
 */
final class SemanticMemoryKey {

    static final String DISPLAY_NAME = "profile.display_name";
    static final String ROLE = "profile.role";
    static final String COMPANY = "profile.company";
    static final String INDUSTRY = "profile.industry";
    static final String COMMUNICATION_STYLE = "preference.communication_style";
    static final String OUTPUT_FORMAT = "preference.output_format";

    private static final Pattern VALID_PATTERN = Pattern.compile(
            "^(preference|profile|conclusion)\\.[a-z0-9_.-]{1,120}$");

    private SemanticMemoryKey() {
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static boolean isValid(String value) {
        return StringUtils.hasText(value) && VALID_PATTERN.matcher(normalize(value)).matches();
    }

    static String legacyKey(MemoryType type, String memoryId) {
        String prefix = switch (type) {
            case PREFERENCE -> "preference.general.";
            case ENTITY -> "profile.general.";
            case CONCLUSION -> "conclusion.general.";
            default -> throw new IllegalArgumentException("unsupported semantic memory type: " + type);
        };
        return prefix + memoryId.toLowerCase(Locale.ROOT);
    }
}
