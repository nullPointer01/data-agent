package com.ai.agent.approval;

/** 审批状态失效通知：只携带定位引用，正文、参数、密钥和授权不进入消息总线。 */
public record ApprovalChanged(String tenantId, String userId, String sessionId, String runId, String approvalId) {
}
