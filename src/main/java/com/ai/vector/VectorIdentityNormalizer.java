package com.ai.vector;

import org.springframework.stereotype.Component;

/**
 * 在写入向量元数据前规范化可选的身份字段。
 *
 * @author data-agent
 */
@Component
public class VectorIdentityNormalizer {

    public static final String ANONYMOUS_TENANT_ID = "anonymous";
    public static final String ANONYMOUS_USER_ID = "anonymous";

    public VectorIdentity normalize(String tenantId, String userId) {
        return new VectorIdentity(normalizeTenantId(tenantId), normalizeUserId(userId));
    }

    public String normalizeTenantId(String tenantId) {
        return hasText(tenantId) ? tenantId : ANONYMOUS_TENANT_ID;
    }

    public String normalizeUserId(String userId) {
        return hasText(userId) ? userId : ANONYMOUS_USER_ID;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
