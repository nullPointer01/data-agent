package com.ai.agent.tool.governance;

import com.ai.agent.approval.AgentApprovalGrant;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 在工具预算和执行之前完成一次性 Registry、Schema、权限、白名单与风险准入。
 *
 * @author data-agent
 */
@Component
public class AgentToolAdmissionService {

    private final AgentToolRegistry registry;
    private final AgentToolArgumentValidator argumentValidator;
    private final AgentToolPolicyEngine policyEngine;

    public AgentToolAdmissionService(AgentToolRegistry registry,
            AgentToolArgumentValidator argumentValidator,
            AgentToolPolicyEngine policyEngine) {
        this.registry = registry;
        this.argumentValidator = argumentValidator;
        this.policyEngine = policyEngine;
    }

    public AgentToolAdmission admit(ToolExecutionRequest request,
            AgentToolInvocationContext invocationContext) {
        return admit(request, invocationContext, null, false);
    }

    public AgentToolAdmission admitApproved(ToolExecutionRequest request,
            AgentToolInvocationContext invocationContext,
            AgentApprovalGrant grant) {
        return admit(request, invocationContext, grant, true);
    }

    private AgentToolAdmission admit(ToolExecutionRequest request,
            AgentToolInvocationContext invocationContext,
            AgentApprovalGrant grant,
            boolean approvedExecution) {
        AgentRunContext runContext = AgentRunScope.current().orElse(null);
        String toolCallId = toolCallId(runContext, request);
        String requestedName = request == null ? "" : request.name();
        AgentToolRegistry.RegisteredTool registered = registry.find(requestedName).orElse(null);
        if (registered == null) {
            return new AgentToolAdmission(AgentToolExecutionStatus.UNKNOWN_TOOL, null, "未知工具");
        }
        AgentToolArgumentValidation validation = argumentValidator.validate(
                request.arguments(), registered.specification().parameters());
        AgentToolAdmission.AdmittedToolCall admitted = new AgentToolAdmission.AdmittedToolCall(
                request, runContext, invocationContext, registered, validation, toolCallId);
        if (!validation.valid()) {
            return new AgentToolAdmission(AgentToolExecutionStatus.INVALID_ARGUMENTS, admitted, validation.error());
        }
        AgentToolPolicyEngine.Decision decision = policyEngine.authorize(
                runContext, invocationContext, registered.descriptor());
        if (!decision.allowed()) {
            return new AgentToolAdmission(decision.status(), admitted, decision.safeMessage());
        }
        if (registered.descriptor().approvalRequired()) {
            if (approvedExecution && grant != null && runContext != null
                    && grant.validFor(
                            runContext.runId(),
                            runContext.tenantId(),
                            toolCallId,
                            registered.descriptor().name())) {
                return new AgentToolAdmission(AgentToolExecutionStatus.SUCCESS, admitted, "");
            }
            if (approvedExecution) {
                return new AgentToolAdmission(
                        AgentToolExecutionStatus.UNAUTHORIZED,
                        admitted,
                        "审批执行凭据无效或已过期");
            }
            return new AgentToolAdmission(
                    AgentToolExecutionStatus.APPROVAL_REQUIRED,
                    admitted,
                    "该工具动作需要人工审批");
        }
        return new AgentToolAdmission(AgentToolExecutionStatus.SUCCESS, admitted, "");
    }

    private String toolCallId(AgentRunContext context, ToolExecutionRequest request) {
        String requestId = request == null ? null : request.id();
        if (requestId == null || requestId.isBlank()) {
            return "tc-" + UUID.randomUUID();
        }
        String runId = context == null ? "no-run" : context.runId();
        byte[] source = (runId + ":" + requestId).getBytes(StandardCharsets.UTF_8);
        return "tc-" + UUID.nameUUIDFromBytes(source);
    }
}
