package com.ai.agent.capability;

import java.util.Map;
import java.util.Set;

/**
 * Tool、Skill 与子 Agent 共享的只读能力合同。
 *
 * @param identity 跨类型稳定标识
 * @param type 能力类型
 * @param version 能力版本或配置修订标识
 * @param name 展示名称
 * @param description 安全描述
 * @param inputContract 输入合同
 * @param outputContract 输出合同
 * @param ownerId 所有者编号，平台能力使用 platform
 * @param tenantId 所属租户，平台能力使用通配租户
 * @param requiredPermissions 执行能力所需权限
 * @param risk 风险元数据
 * @param lifecycle 生命周期状态
 * @param availability 当前运行时可用性
 * @author data-agent
 */
public record AgentCapabilityDescriptor(
        String identity,
        AgentCapabilityType type,
        String version,
        String name,
        String description,
        Map<String, Object> inputContract,
        Map<String, Object> outputContract,
        String ownerId,
        String tenantId,
        Set<String> requiredPermissions,
        RiskMetadata risk,
        LifecycleState lifecycle,
        Availability availability) {

    public AgentCapabilityDescriptor {
        description = description == null ? "" : description.trim();
        inputContract = inputContract == null ? Map.of() : Map.copyOf(inputContract);
        outputContract = outputContract == null ? Map.of() : Map.copyOf(outputContract);
        requiredPermissions = requiredPermissions == null ? Set.of() : Set.copyOf(requiredPermissions);
    }

    /** 能力注册生命周期。 */
    public enum LifecycleState {
        ACTIVE,
        DISABLED
    }

    /** 跨能力类型的保守风险等级。 */
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    /**
     * 能力风险摘要。assessmentSource 说明风险来自工具声明还是平台策略推导。
     */
    public record RiskMetadata(
            RiskLevel level,
            boolean readOnly,
            boolean idempotent,
            boolean approvalRequired,
            boolean externalAccess,
            String assessmentSource) {
    }

    /**
     * 当前运行环境中的能力准入状态。
     */
    public record Availability(boolean available, String reasonCode, String summary) {

        public static Availability ready() {
            return new Availability(true, "AVAILABLE", "当前可绑定");
        }

        public static Availability unavailable(String reasonCode, String summary) {
            return new Availability(false, reasonCode, summary);
        }
    }
}
