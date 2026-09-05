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
                .toList();
    }

    /**
     * 按工具名过滤，返回指定工具的规格列表。
     *
     * @param toolNames 要保留的工具方法名，为空时返回全部
     * @return 过滤后的工具规格
     */
    public List<ToolSpecification> buildToolSpecifications(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return buildToolSpecifications();
        }
        return registry.specifications().stream()
                .filter(specification -> registry.isEnabled(specification.name()))
                .filter(spec -> toolNames.contains(spec.name()))
                .toList();
    }

    /**
     * 返回所有已注册工具的名称和描述，供前端展示可选工具。
     *
     * @return 工具信息列表
     */
    public List<ToolInfo> getAllToolInfo() {
        return buildToolSpecifications().stream()
                .map(spec -> new ToolInfo(spec.name(), spec.description()))
                .toList();
    }

    /**
     * 工具摘要信息。
     *
     * @param name 工具名
     * @param description 工具描述
     */
    public record ToolInfo(String name, String description) {
    }
}
