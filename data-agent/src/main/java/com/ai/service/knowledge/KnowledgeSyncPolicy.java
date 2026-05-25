package com.ai.service.knowledge;

import org.springframework.stereotype.Component;

/**
 * Normalizes and validates knowledge synchronization options.
 *
 * @author data-agent
 */
@Component
public class KnowledgeSyncPolicy {

    public static final String SYNC_TYPE_URL = "url";
    public static final String SYNC_TYPE_GIT = "git";

    private static final String DEFAULT_CRON_EXPRESSION = "0 0 * * *";

    /**
     * Resolves blank sync type to the default URL type and validates known types.
     *
     * @param syncType requested sync type
     * @return normalized sync type
     */
    public String resolveSyncType(String syncType) {
        if (syncType == null || syncType.isBlank()) {
            return SYNC_TYPE_URL;
        }
        if (!SYNC_TYPE_URL.equals(syncType) && !SYNC_TYPE_GIT.equals(syncType)) {
            throw new IllegalArgumentException("不支持的同步类型: " + syncType);
        }
        return syncType;
    }

    /**
     * Resolves blank cron expression to the product default.
     *
     * @param cronExpression requested cron expression
     * @return normalized cron expression
     */
    public String resolveCronExpression(String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            return DEFAULT_CRON_EXPRESSION;
        }
        return cronExpression;
    }

    /**
     * Truncates a status message to fit database storage.
     *
     * @param value status text
     * @param maxLength maximum length
     * @return truncated text
     */
    public String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
