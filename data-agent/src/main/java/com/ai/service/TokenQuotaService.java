package com.ai.service;

import com.ai.exception.QuotaExceededException;
import com.ai.repository.TokenUsageRepository;
import com.ai.security.SysUser;
import com.ai.security.SysUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户每日 Token 配额检查与查询服务。
 *
 * @author data-agent
 */
@Service
public class TokenQuotaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenQuotaService.class);
    private static final long UNLIMITED_QUOTA = -1L;
    private static final long ZERO_LIMIT_SENTINEL = 0L;

    private final long globalDailyLimit;
    private final TokenUsageRepository tokenUsageRepository;
    private final SysUserRepository sysUserRepository;

    public TokenQuotaService(TokenUsageRepository tokenUsageRepository,
            SysUserRepository sysUserRepository,
            @Value("${app.token-quota.daily-limit-per-user:1000000}") long globalDailyLimit) {
        this.tokenUsageRepository = tokenUsageRepository;
        this.sysUserRepository = sysUserRepository;
        this.globalDailyLimit = globalDailyLimit;
    }

    /**
     * 检查当前用户的每日配额是否已耗尽。
     *
     * @param userId 用户编号
     */
    @Transactional(readOnly = true)
    public void checkDailyQuota(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }

        long limit = resolveLimit(userId);
        if (limit == UNLIMITED_QUOTA) {
            return;
        }

        long usedTokens = getUsedTodayTokens(userId);
        if (usedTokens >= limit) {
            LOGGER.warn("Token 配额已耗尽: userId={}, used={}, limit={}", userId, usedTokens, limit);
            throw new QuotaExceededException(
                    String.format("今日 Token 用量已达上限（已用 %,d / 上限 %,d），请明日再试或联系管理员提升配额",
                            usedTokens, limit));
        }
    }

    /**
     * 查询剩余每日配额。
     *
     * @param userId 用户编号
     * @return 剩余 Token 配额
     */
    @Transactional(readOnly = true)
    public long getRemainingQuota(String userId) {
        if (!StringUtils.hasText(userId)) {
            return globalDailyLimit;
        }
        long limit = resolveLimit(userId);
        if (limit == UNLIMITED_QUOTA) {
            return Long.MAX_VALUE;
        }
        long used = getUsedTodayTokens(userId);
        return Math.max(0, limit - used);
    }

    private long resolveLimit(String userId) {
        SysUser user = sysUserRepository.findById(userId).orElse(null);
        if (user != null && user.getDailyTokenLimit() != ZERO_LIMIT_SENTINEL) {
            return user.getDailyTokenLimit();
        }
        return globalDailyLimit;
    }

    private long getUsedTodayTokens(String userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        Long used = tokenUsageRepository.sumTotalTokensByUserIdSince(userId, startOfDay);
        return used != null ? used : 0;
    }
}
