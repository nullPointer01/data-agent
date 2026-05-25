package com.ai.config;

import com.ai.security.SecurityContextHelper;
import com.ai.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Web interceptor enforcing per-user API rate limits.
 *
 * @author data-agent
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";
    private static final String RATE_LIMITED_CODE = "RATE_LIMITED";

    private final int requestsPerMinute;
    private final RateLimitService rateLimitService;
    private final SecurityContextHelper securityContextHelper;

    public RateLimitInterceptor(RateLimitService rateLimitService,
            SecurityContextHelper securityContextHelper,
            @Value("${app.rate-limit.requests-per-minute:20}") int requestsPerMinute) {
        this.rateLimitService = rateLimitService;
        this.securityContextHelper = securityContextHelper;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String userId = securityContextHelper.getCurrentUserId();
        if (userId == null) {
            return true;
        }

        RateLimitService.RateLimitResult result = rateLimitService.checkUserMinuteLimit(userId, requestsPerMinute);
        if (!result.allowed()) {
            LOGGER.warn("Rate limit exceeded for user={}, count={}/{}", userId, result.count(), result.limit());
            writeRateLimitedResponse(response);
            return false;
        }
        return true;
    }

    private void writeRateLimitedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HTTP_TOO_MANY_REQUESTS);
        response.setContentType(JSON_CONTENT_TYPE);
        String message = "请求过于频繁，请稍后再试（每分钟最多" + requestsPerMinute + "次）";
        response.getWriter().write("""
                {"success":false,"message":"%s","code":"%s"}
                """.formatted(message, RATE_LIMITED_CODE));
    }
}
