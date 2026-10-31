package com.ai.token;

import java.time.LocalDate;

/** 一次实际模型尝试的持久预占身份；重试必须创建新身份，结算不依赖回调线程登录态。 */
public record TokenReservation(String invocationId, String tenantId, String userId, LocalDate quotaDate,
        long estimatedInputTokens, long reservedTokens, int maxOutputTokens,
        String modelId, String modelName, String sessionId, String runId, String state, long chargedTokens) { }
