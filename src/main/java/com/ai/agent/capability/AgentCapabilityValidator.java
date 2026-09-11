package com.ai.agent.capability;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 在能力进入统一目录或 Agent allowlist 前校验完整治理合同。
 *
 * @author data-agent
 */
@Component
public class AgentCapabilityValidator {

    /**
     * 校验单个能力描述符。
     *
     * @param descriptor 待注册描述符
     */
    public void validate(AgentCapabilityDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("能力描述符不能为空");
        }
        requireText(descriptor.identity(), "能力标识");
        if (descriptor.type() == null) {
            throw invalid(descriptor.identity(), "能力类型不能为空");
        }
        if (!descriptor.type().owns(descriptor.identity())) {
            throw invalid(descriptor.identity(), "能力标识与类型不一致");
        }
        requireText(descriptor.version(), "能力版本");
        requireText(descriptor.name(), "能力名称");
        requireContract(descriptor.inputContract(), "输入合同", descriptor.identity());
        requireContract(descriptor.outputContract(), "输出合同", descriptor.identity());
        requireText(descriptor.ownerId(), "能力所有者");
        requireText(descriptor.tenantId(), "能力租户");
        if (descriptor.requiredPermissions() == null || descriptor.requiredPermissions().isEmpty()) {
            throw invalid(descriptor.identity(), "能力所需权限不能为空");
        }
        descriptor.requiredPermissions().forEach(permission -> requireText(permission, "能力权限码"));
        validateRisk(descriptor);
        if (descriptor.lifecycle() == null) {
            throw invalid(descriptor.identity(), "能力生命周期不能为空");
        }
        validateAvailability(descriptor);
    }

    private void validateRisk(AgentCapabilityDescriptor descriptor) {
        AgentCapabilityDescriptor.RiskMetadata risk = descriptor.risk();
        if (risk == null || risk.level() == null) {
            throw invalid(descriptor.identity(), "能力风险元数据不能为空");
        }
        requireText(risk.assessmentSource(), "风险评估来源");
        if (risk.approvalRequired() && risk.readOnly()) {
            throw invalid(descriptor.identity(), "审批型能力不能声明为只读");
        }
    }

    private void validateAvailability(AgentCapabilityDescriptor descriptor) {
        AgentCapabilityDescriptor.Availability availability = descriptor.availability();
        if (availability == null) {
            throw invalid(descriptor.identity(), "能力可用性不能为空");
        }
        requireText(availability.reasonCode(), "能力可用性原因码");
        requireText(availability.summary(), "能力可用性说明");
        if (descriptor.lifecycle() == AgentCapabilityDescriptor.LifecycleState.DISABLED
                && availability.available()) {
            throw invalid(descriptor.identity(), "已停用能力不能标记为可用");
        }
    }

    private void requireContract(Map<String, Object> contract, String field, String identity) {
        if (contract == null || contract.isEmpty()) {
            throw invalid(identity, field + "不能为空");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }

    private IllegalArgumentException invalid(String identity, String message) {
        return new IllegalArgumentException("能力注册失败 [" + identity + "]: " + message);
    }
}
