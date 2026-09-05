package com.ai.logging;

/**
 * 请求链路关联上下文常量。
 *
 * @author data-agent
 */
public final class RequestCorrelationContext {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_METHOD = "method";
    public static final String MDC_PATH = "path";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_USERNAME = "username";
    public static final String MDC_TENANT_ID = "tenantId";

    private RequestCorrelationContext() {
    }
}
