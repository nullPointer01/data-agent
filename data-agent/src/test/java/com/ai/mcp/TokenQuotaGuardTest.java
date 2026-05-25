package com.ai.mcp;

import com.ai.exception.QuotaExceededException;
import com.ai.security.SecurityContextHelper;
import com.ai.service.TokenQuotaService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenQuotaGuardTest {

    @Test
    void checkCurrentUserQuotaReturnsAllowedWhenQuotaPasses() {
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        TokenQuotaService tokenQuotaService = mock(TokenQuotaService.class);
        when(securityContextHelper.getCurrentUserId()).thenReturn("user-1");

        TokenQuotaGuard guard = new TokenQuotaGuard(securityContextHelper, tokenQuotaService);

        TokenQuotaGuard.QuotaCheckResult result = guard.checkCurrentUserQuota();

        assertTrue(result.allowed());
        assertNull(result.message());
        verify(tokenQuotaService).checkDailyQuota("user-1");
    }

    @Test
    void checkCurrentUserQuotaReturnsDeniedWhenQuotaExceeded() {
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        TokenQuotaService tokenQuotaService = mock(TokenQuotaService.class);
        when(securityContextHelper.getCurrentUserId()).thenReturn("user-1");
        doThrow(new QuotaExceededException("额度已用完"))
                .when(tokenQuotaService).checkDailyQuota("user-1");

        TokenQuotaGuard guard = new TokenQuotaGuard(securityContextHelper, tokenQuotaService);

        TokenQuotaGuard.QuotaCheckResult result = guard.checkCurrentUserQuota();

        assertFalse(result.allowed());
        assertTrue(result.message().contains("额度"));
    }
}
