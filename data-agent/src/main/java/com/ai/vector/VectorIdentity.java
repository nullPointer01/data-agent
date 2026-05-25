package com.ai.vector;

/**
 * Normalized tenant and user identity for vector metadata.
 *
 * @author data-agent
 */
public record VectorIdentity(String tenantId, String userId) {
}
