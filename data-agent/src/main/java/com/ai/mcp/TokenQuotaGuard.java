package com.ai.mcp;

import com.ai.exception.QuotaExceededException;
import com.ai.security.SecurityContextHelper;
import com.ai.service.TokenQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Guard for current user's model token quota.
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
     * Checks current user's daily token quota.
     *
     * @return quota check result
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
     * Result of quota checking.
     *
     * @param allowed whether request is allowed
     * @param message denial message
     */
    public record QuotaCheckResult(boolean allowed, String message) {

        /**
         * Creates allowed result.
         *
         * @return allowed result
         */
        public static QuotaCheckResult allow() {
            return new QuotaCheckResult(true, null);
        }

        /**
         * Creates denied result.
         *
         * @param message denial message
         * @return denied result
         */
        public static QuotaCheckResult deny(String message) {
            return new QuotaCheckResult(false, message);
        }
    }
}
