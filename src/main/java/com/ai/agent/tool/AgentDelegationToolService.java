package com.ai.agent.tool;

import com.ai.agent.AgentExecutionContext;
import com.ai.agent.AgentRunOrigin;
import com.ai.agent.ConfigurableAgentExecutor;
import com.ai.agent.capability.AgentCapabilityBindingSnapshot;
import com.ai.agent.capability.AgentCapabilityScope;
import com.ai.agent.capability.AgentDelegationGuard;
import com.ai.agent.runtime.AgentExecutionMode;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.PersonalAgentExecutionModeResolver;
import com.ai.agent.runtime.planning.RequestExecutionPlan;
import com.ai.memory.dto.MemoryContext;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.repository.AgentProfileRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 将模型的子 Agent 请求转换为受能力快照和根 Run 治理的配置 Agent 执行。
 *
 * @author data-agent
 */
@Service
public class AgentDelegationToolService {

    private static final int MAX_TASK_LENGTH = 12_000;
    private static final String AGENT_ID_PREFIX = "agent:";

    private final AgentProfileRepository agentRepository;
    private final AgentDelegationGuard delegationGuard;
    private final ObjectProvider<PersonalAgentExecutionModeResolver> modeResolverProvider;
    private final ObjectProvider<ConfigurableAgentExecutor> executorProvider;

    public AgentDelegationToolService(AgentProfileRepository agentRepository,
            AgentDelegationGuard delegationGuard,
            ObjectProvider<PersonalAgentExecutionModeResolver> modeResolverProvider,
            ObjectProvider<ConfigurableAgentExecutor> executorProvider) {
        this.agentRepository = agentRepository;
        this.delegationGuard = delegationGuard;
        this.modeResolverProvider = modeResolverProvider;
        this.executorProvider = executorProvider;
    }

    /**
     * 委派任务给当前 Agent 已绑定的子 Agent。
     *
     * @param agentId 子 Agent 稳定编号
     * @param task 要交给子 Agent 的明确任务
     * @return 子 Agent 最终结果
     */
    public String delegateToAgent(String agentId, String task) {
        String childAgentId = normalizeAgentId(agentId);
        String delegatedTask = requireTask(task);
        AgentCapabilityBindingSnapshot parentSnapshot = AgentCapabilityScope.current()
                .orElseThrow(() -> rejected("DELEGATION_SCOPE_MISSING", "当前执行没有 Agent 能力作用域"));
        if (!parentSnapshot.allowsSubAgent(childAgentId)) {
            throw rejected("SUB_AGENT_NOT_BOUND", "目标子 Agent 未绑定、已停用或无权使用");
        }

        AgentRunContext runContext = AgentRunScope.current()
                .orElseThrow(() -> rejected("DELEGATION_RUN_SCOPE_MISSING", "当前执行没有根 Run 上下文"));
        AgentProfile childProfile = agentRepository
                .findByAgentIdAndTenantId(childAgentId, runContext.tenantId())
                .filter(AgentProfile::isEnabled)
                .orElseThrow(() -> rejected("SUB_AGENT_UNAVAILABLE", "目标子 Agent 已删除、停用或不属于当前租户"));

        AnalysisRequest childRequest = childRequest(runContext.executionContext().getRequest(), childAgentId,
                delegatedTask);
        AgentExecutionContext childExecutionContext = new AgentExecutionContext(
                childRequest,
                runContext.executionContext().getSession(),
                runContext.executionContext().getFileContent(),
                AgentRunOrigin.DELEGATED);
        RequestExecutionPlan childPlan = modeResolverProvider.getObject()
                .plan(childProfile, childExecutionContext);
        AgentExecutionMode childMode = childPlan.mode();

        return delegationGuard.call(parentSnapshot.agentId(), childAgentId, () -> {
            AnalysisResponse response = executorProvider.getObject().execute(
                    childProfile,
                    childRequest,
                    childExecutionContext.getFileContent(),
                    MemoryContext.empty(),
                    childMode,
                    childPlan);
            if (response == null || !response.isSuccess() || response.getResult() == null) {
                throw new IllegalStateException("子 Agent 未返回可用结果");
            }
            return "子 Agent [" + childProfile.getName() + "] 返回:\n" + response.getResult();
        });
    }

    private AnalysisRequest childRequest(AnalysisRequest parentRequest, String childAgentId, String task) {
        AnalysisRequest request = parentRequest == null ? new AnalysisRequest() : parentRequest.copy();
        request.setQuestion(task);
        request.setAgentId(childAgentId);
        request.setSkillId(null);
        // 子 Agent 必须使用自己的 Profile 模型，不能继承父请求的临时模型覆盖。
        request.setModelId(null);
        return request;
    }

    private String normalizeAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw rejected("DELEGATION_TARGET_MISSING", "子 Agent 编号不能为空");
        }
        String normalized = agentId.trim();
        normalized = normalized.startsWith(AGENT_ID_PREFIX)
                ? normalized.substring(AGENT_ID_PREFIX.length())
                : normalized;
        if (normalized.isBlank()) {
            throw rejected("DELEGATION_TARGET_MISSING", "子 Agent 编号不能为空");
        }
        return normalized;
    }

    private String requireTask(String task) {
        if (task == null || task.isBlank()) {
            throw rejected("DELEGATION_TASK_MISSING", "委派任务不能为空");
        }
        String normalized = task.trim();
        if (normalized.length() > MAX_TASK_LENGTH) {
            throw rejected("DELEGATION_TASK_TOO_LONG", "委派任务过长");
        }
        return normalized;
    }

    private AgentDelegationGuard.DelegationRejectedException rejected(String reasonCode, String message) {
        return new AgentDelegationGuard.DelegationRejectedException(reasonCode, message);
    }
}
