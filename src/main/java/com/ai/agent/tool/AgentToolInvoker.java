package com.ai.agent.tool;

import com.ai.agent.approval.AgentApprovalGrant;
import com.ai.agent.tool.governance.AgentToolRegistry;
import com.ai.agent.tool.governance.AgentToolAdmission;
import com.ai.agent.tool.governance.AgentToolExecutionPipeline;
import com.ai.agent.tool.governance.AgentToolExecutionResult;
import com.ai.agent.tool.governance.AgentToolInvocationContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 工具注册与执行中心：从 {@link AgentTools} 的 {@code @Tool} 注解自动生成工具规格并按参数名绑定执行。
 *
 * <p>工具的定义（Schema）与执行（参数绑定、反射调用）均由 LangChain4j 注解体系驱动，
 * 新增工具只需在 {@link AgentTools} 上添加一个带 {@code @Tool}/{@code @P} 注解的方法。</p>
 *
 * @author data-agent
 */
@Component
public class AgentToolInvoker {

    private final AgentToolRegistry registry;
    private final AgentToolExecutionPipeline executionPipeline;

    public AgentToolInvoker(AgentToolRegistry registry, AgentToolExecutionPipeline executionPipeline) {
        this.registry = registry;
        this.executionPipeline = executionPipeline;
    }

    /**
     * 执行一次原生工具调用请求，参数按名称自动绑定。
     *
     * @param request 原生工具执行请求
     * @return 工具结果文本
     */
    public String invoke(ToolExecutionRequest request) {
        return invokeStructured(request, null).toModelObservation();
    }

    public String invoke(ToolExecutionRequest request, AgentToolInvocationContext invocationContext) {
        return invokeStructured(request, invocationContext).toModelObservation();
    }

    public AgentToolExecutionResult invokeStructured(ToolExecutionRequest request,
            AgentToolInvocationContext invocationContext) {
        return executionPipeline.execute(request, invocationContext);
    }

    public AgentToolAdmission admit(ToolExecutionRequest request,
            AgentToolInvocationContext invocationContext) {
        return executionPipeline.admit(request, invocationContext);
    }

    public AgentToolExecutionResult invokeStructured(AgentToolAdmission admission,
            AgentToolInvocationContext invocationContext) {
        return executionPipeline.execute(admission, invocationContext);
    }

    public AgentToolExecutionResult invokeApproved(ToolExecutionRequest request,
            AgentToolInvocationContext invocationContext,
            AgentApprovalGrant grant) {
        return executionPipeline.executeApproved(request, invocationContext, grant);
    }

    /**
     * 返回全部工具规格列表。
     *
     * @return 工具规格
     */
    public List<ToolSpecification> buildToolSpecifications() {
        return registry.specifications().stream()
                .filter(specification -> registry.isEnabled(specification.name()))
                .filter(specification -> !AgentTools.isCapabilityAdapter(specification.name()))
                .toList();
    }

    /**
     * 按服务端已经解析出的精确名称集合返回工具规格。
     *
     * <p>与旧列表接口不同，空集合严格表示零工具；该入口允许 Configurable Agent
     * 在有有效 Skill 绑定时显式加入 Harness 内部适配器。</p>
     *
     * @param toolNames 服务端允许的精确工具名称
     * @return 已启用且名称匹配的工具规格
     */
    public List<ToolSpecification> buildExactToolSpecifications(Set<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        return registry.specifications().stream()
                .filter(specification -> registry.isEnabled(specification.name()))
                .filter(specification -> toolNames.contains(specification.name()))
                .toList();
    }

}
