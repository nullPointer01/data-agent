package com.ai.mcp;

import com.ai.exception.QuotaExceededException;
import com.ai.security.SecurityContextHelper;
import com.ai.service.TokenQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 当前用户模型令牌配额保护器。
 *
 * @author data-agent
 */
@Component
public class TokenQuotaGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenQuotaGuard.class);

    private final SecurityContextHelper securityContextHelper;
    private final TokenQuotaService tokenQuotaService;

    public TokenQuotaGuard(SecurityContextHelper securityContextHelper, TokenQuotaService tokenQuotaService) {
        this.securityContextHelper = securityContextHelper;
        this.tokenQuotaService = tokenQuotaService;
    }

    /**
     * 检查当前用户的每日令牌配额。
     *
     * @return 配额检查结果
     */
    public QuotaCheckResult checkCurrentUserQuota() {
        String userId = securityContextHelper.getCurrentUserId();
        try {
            tokenQuotaService.checkDailyQuota(userId);
            return QuotaCheckResult.allow();
        } catch (QuotaExceededException e) {
            LOGGER.warn("Token quota exceeded for user={}", userId);
            return QuotaCheckResult.deny(e.getMessage());
        }
    }

    /**
     * 配额检查结果。
     *
     * @param allowed 请求是否允许
     * @param message 拒绝原因信息
     */
    public record QuotaCheckResult(boolean allowed, String message) {

        /**
         * 创建允许结果。
         *
         * @return 允许结果
         */
        public static QuotaCheckResult allow() {
            return new QuotaCheckResult(true, null);
        }

        /**
         * 创建拒绝结果。
         *
         * @param message 拒绝原因信息
         * @return 拒绝结果
         */
        public static QuotaCheckResult deny(String message) {
            return new QuotaCheckResult(false, message);
        }
    }
}
