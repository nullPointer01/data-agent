package com.ai.agent.durable;

import java.time.Instant;

/**
 * 成功领取的数据库恢复租约。
 *
 * @param runId Run ID
 * @param owner 节点租约标识
 * @param version 领取后的 Run 版本
 * @param leaseUntil 租约截止时间
 * @author data-agent
 */
public record AgentRunLease(String runId, String owner, long version, Instant leaseUntil) {
}
