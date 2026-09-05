package com.ai.agent.tool.governance;

import java.time.Duration;

/**
 * 一个已注册 Agent 工具的不可变治理合同。
 *
 * @param name 工具名称
 * @param risk 风险等级
 * @param readOnly 是否只读
 * @param idempotent 是否幂等
 * @param retryable 是否允许对瞬时故障重试
 * @param timeout 单次尝试超时
 * @param maxAttempts 单次逻辑调用最大尝试数
 * @param requiredPermission 所需 RBAC 权限码
 * @param approvalRequired 是否必须人工审批
 * @param approvalPermission 审批人所需的工具级权限码
 * @param maxResultLength 最大安全结果字符数
 * @author data-agent
 */
public record AgentToolDescriptor(
        String name,
        AgentToolRiskLevel risk,
        boolean readOnly,
        boolean idempotent,
        boolean retryable,
        Duration timeout,
        int maxAttempts,
        String requiredPermission,
        boolean approvalRequired,
        String approvalPermission,
        int maxResultLength) {

    public AgentToolDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工具名称不能为空");
        }
        if (risk == null) {
            throw new IllegalArgumentException("工具风险等级不能为空: " + name);
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("工具 timeout 必须为正数: " + name);
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("工具最大尝试数必须为正数: " + name);
        }
        if (requiredPermission == null || requiredPermission.isBlank()) {
            throw new IllegalArgumentException("工具所需权限不能为空: " + name);
        }
        if (maxResultLength <= 0) {
            throw new IllegalArgumentException("工具最大结果长度必须为正数: " + name);
        }
        if (retryable && (!readOnly || !idempotent)) {
            throw new IllegalArgumentException("可重试工具必须同时只读且幂等: " + name);
        }
        if (!retryable && maxAttempts != 1) {
            throw new IllegalArgumentException("不可重试工具的最大尝试数必须为 1: " + name);
        }
        if (approvalRequired && (approvalPermission == null || approvalPermission.isBlank())) {
            throw new IllegalArgumentException("审批型工具必须声明审批权限: " + name);
        }
        if (approvalRequired && retryable) {
            throw new IllegalArgumentException("审批型工具不能启用管道自动重试: " + name);
        }
        name = name.trim();
        requiredPermission = requiredPermission.trim();
        approvalPermission = approvalPermission == null ? "" : approvalPermission.trim();
    }

    /**
     * 从方法上的治理注解创建描述符。
     *
     * @param name LangChain4j 工具名称
     * @param policy 治理注解
     * @return 校验后的描述符
     */
    public static AgentToolDescriptor from(String name, AgentToolPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("工具缺少 @AgentToolPolicy: " + name);
        }
        return new AgentToolDescriptor(name, policy.risk(), policy.readOnly(), policy.idempotent(),
                policy.retryable(), Duration.ofMillis(policy.timeoutMs()), policy.maxAttempts(),
                policy.requiredPermission(), policy.approvalRequired(), policy.approvalPermission(),
                policy.maxResultLength());
    }
}
