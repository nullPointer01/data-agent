package com.ai.agent.tool.governance;

import com.ai.agent.durable.AgentDurableRuntimeProperties;
import com.ai.agent.tool.AgentTools;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 工具规格、执行器和治理描述符的唯一服务端目录。
 *
 * @author data-agent
 */
@Component
public class AgentToolRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentToolRegistry.class);

    private final Map<String, RegisteredTool> tools;
    private final List<ToolSpecification> specifications;
    private final Set<String> enabledToolNames;

    public AgentToolRegistry(AgentTools agentTools,
            AgentToolGovernanceProperties properties,
            AgentDurableRuntimeProperties durableProperties) {
        Map<String, RegisteredTool> registered = new LinkedHashMap<>();
        List<Method> methods = Arrays.stream(AgentTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .sorted(Comparator.comparing(Method::getName))
                .toList();
        for (Method method : methods) {
            ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
            AgentToolDescriptor descriptor = AgentToolDescriptor.from(specification.name(),
                    method.getAnnotation(AgentToolPolicy.class));
            RegisteredTool entry = new RegisteredTool(specification,
                    DefaultToolExecutor.builder()
                            .object(agentTools)
                            .originalMethod(method)
                            .methodToInvoke(method)
                            .wrapToolArgumentsExceptions(true)
                            .propagateToolExecutionExceptions(true)
                            .build(),
                    descriptor);
            if (registered.putIfAbsent(specification.name(), entry) != null) {
                throw new IllegalStateException("Agent 工具名称重复: " + specification.name());
            }
        }
        this.tools = Map.copyOf(registered);
        this.specifications = registered.values().stream().map(RegisteredTool::specification).toList();
        this.enabledToolNames = resolveEnabledTools(
                properties.getEnabledTools(), registered, durableProperties);
        LOGGER.info("Registered {} governed Agent tools, enabled={}", tools.size(), enabledToolNames.size());
    }

    private Set<String> resolveEnabledTools(Set<String> configured,
            Map<String, RegisteredTool> registered,
            AgentDurableRuntimeProperties durableProperties) {
        Set<String> registeredNames = registered.keySet();
        Set<String> requested = configured == null || configured.isEmpty()
                ? Set.copyOf(registeredNames)
                : Set.copyOf(configured);
        Set<String> unknown = requested.stream()
                .filter(name -> !registeredNames.contains(name))
                .collect(Collectors.toUnmodifiableSet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("启用列表包含未注册 Agent 工具: " + unknown);
        }
        Set<String> approvalTools = registered.values().stream()
                .map(RegisteredTool::descriptor)
                .filter(AgentToolDescriptor::approvalRequired)
                .map(AgentToolDescriptor::name)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> unknownApprovalTools = durableProperties.getEnabledApprovalTools().stream()
                .filter(name -> !approvalTools.contains(name))
                .collect(Collectors.toUnmodifiableSet());
        if (!unknownApprovalTools.isEmpty()) {
            throw new IllegalArgumentException("审批工具启用列表包含非审批型或未注册工具: " + unknownApprovalTools);
        }
        boolean approvalExecutionEnabled = durableProperties.isEnabled()
                && durableProperties.isSandboxToolEnabled();
        return requested.stream()
                .filter(name -> !approvalTools.contains(name)
                        || (approvalExecutionEnabled
                        && durableProperties.getEnabledApprovalTools().contains(name)))
                .collect(Collectors.toUnmodifiableSet());
    }

    public Optional<RegisteredTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<ToolSpecification> specifications() {
        return specifications;
    }

    public Set<String> registeredToolNames() {
        return tools.keySet();
    }

    public Set<String> enabledToolNames() {
        return enabledToolNames;
    }

    public boolean isEnabled(String name) {
        return enabledToolNames.contains(name);
    }

    /**
     * 一个名称下严格配对的模型规格、底层执行器和治理合同。
     *
     * @param specification 模型可见规格
     * @param executor 底层反射执行器
     * @param descriptor 服务端治理合同
     */
    public record RegisteredTool(
            ToolSpecification specification,
            ToolExecutor executor,
            AgentToolDescriptor descriptor) {
    }
}
