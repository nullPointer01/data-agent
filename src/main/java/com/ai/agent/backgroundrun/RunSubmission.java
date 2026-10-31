package com.ai.agent.backgroundrun;

import java.time.Instant;

/** 不复制 Run 状态机；只保存加密请求、幂等键、首次执行租约与日志容量。 */
public record RunSubmission(String runId, String tenantId, String userId, String requestKey,
        String fingerprint, String payload, boolean started, String leaseOwner, Instant leaseUntil,
        int eventCount, long eventBytes) { }
