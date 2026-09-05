package com.ai.agent.tool.governance;

import com.ai.agent.runtime.AgentRunContext;
import org.springframework.stereotype.Component;

/**
 * 对 Run 主体、调用者白名单和风险上限取交集的默认拒绝策略。
 *
 * @author data-agent
 */
@Component
public class AgentToolPolicyEngine {

    private final AgentToolRegistry registry;
    private final AgentToolGovernanceProperties properties;

    public AgentToolPolicyEngine(AgentToolRegistry registry, AgentToolGovernanceProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    public Decision authorize(AgentRunContext runContext, AgentToolInvocationContext invocationContext,
            AgentToolDescriptor descriptor) {
        if (runContext == null || invocationContext == null) {
            return Decision.deny(AgentToolExecutionStatus.UNAUTHORIZED, "工具调用缺少服务端执行上下文");
        }
        if (!registry.isEnabled(descriptor.name()) || !invocationContext.allows(descriptor.name())) {
            return Decision.deny(AgentToolExecutionStatus.UNAUTHORIZED, "当前 Agent 未获准调用该工具");
        }
        if (!runContext.toolAuthorization().permits(descriptor.name(), descriptor.requiredPermission())) {
            return Decision.deny(AgentToolExecutionStatus.UNAUTHORIZED, "当前用户未获准调用该工具");
        }
        if (descriptor.risk().ordinal() > properties.getMaxRisk().ordinal()) {
            return Decision.deny(AgentToolExecutionStatus.POLICY_DENIED, "工具风险等级超过当前环境上限");
        }
        return Decision.allow();
    }

    public record Decision(boolean allowed, AgentToolExecutionStatus status, String safeMessage) {

        public static Decision allow() {
            return new Decision(true, AgentToolExecutionStatus.SUCCESS, "");
        }

        public static Decision deny(AgentToolExecutionStatus status, String safeMessage) {
            return new Decision(false, status, safeMessage);
        }
    }
}
