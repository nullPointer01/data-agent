package com.ai.agent.tool.governance;

/**
 * 工具管道传给审批暂停层的安全元数据，不包含原始参数。
 *
 * @param risk 风险等级
 * @param approvalPermission 审批人所需工具级权限
 * @param safeArgumentSummary 脱敏参数摘要
 * @author data-agent
 */
public record AgentToolApprovalContext(
        AgentToolRiskLevel risk,
        String approvalPermission,
        String safeArgumentSummary) {
}
