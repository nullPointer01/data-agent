package com.ai.logging;
import com.ai.security.auth.JwtAuthenticationFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 为每个 HTTP 请求建立日志关联编号。
 *
 * <p>该过滤器只负责请求级上下文，不读取认证信息。认证成功后的用户和租户信息由
 * {@link com.ai.security.JwtAuthenticationFilter} 继续补充到 MDC。</p>
 *
 * @author data-agent
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final Pattern SAFE_REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]+");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        try {
            putRequestContext(request, requestId);
            response.setHeader(RequestCorrelationContext.HEADER_REQUEST_ID, requestId);
            filterChain.doFilter(request, response);
        } finally {
            clearRequestContext();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(RequestCorrelationContext.HEADER_REQUEST_ID);
        if (isSafeRequestId(requestId)) {
            return requestId.trim();
        }
        return UUID.randomUUID().toString();
    }

    private boolean isSafeRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return false;
        }
        String trimmedRequestId = requestId.trim();
        return trimmedRequestId.length() <= MAX_REQUEST_ID_LENGTH
                && SAFE_REQUEST_ID_PATTERN.matcher(trimmedRequestId).matches();
    }

    private void putRequestContext(HttpServletRequest request, String requestId) {
        MDC.put(RequestCorrelationContext.MDC_REQUEST_ID, requestId);
        MDC.put(RequestCorrelationContext.MDC_METHOD, request.getMethod());
        MDC.put(RequestCorrelationContext.MDC_PATH, request.getRequestURI());
    }

    private void clearRequestContext() {
        MDC.remove(RequestCorrelationContext.MDC_REQUEST_ID);
        MDC.remove(RequestCorrelationContext.MDC_METHOD);
        MDC.remove(RequestCorrelationContext.MDC_PATH);
        MDC.remove(RequestCorrelationContext.MDC_USER_ID);
        MDC.remove(RequestCorrelationContext.MDC_USERNAME);
        MDC.remove(RequestCorrelationContext.MDC_TENANT_ID);
    }
}
