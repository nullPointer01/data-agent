package com.ai.vector;

/**
 * 向量元数据的规范化租户和用户身份。
 *
 * @author data-agent
 */
public record VectorIdentity(String tenantId, String userId) {
}
