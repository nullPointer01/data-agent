package com.ai.agent.tool.governance;

import com.ai.agent.durable.AgentDurableRuntimeProperties;
import com.ai.agent.tool.AgentTools;
import com.ai.integration.mcp.McpToolCatalog;
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
 * ToolSpecifications 负责从框架的 @Tool/@P 生成 JSON Schema，DefaultToolExecutor 负责参数转换与方法调用。
 * 本目录不手写 Function Calling 协议；权限、审批和幂等由调用前的治理管道负责。
 *
 * @author data-agent
 */
@Component
public class AgentToolRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentToolRegistry.class);

    private final Map<String, RegisteredTool> tools;
    private final Set<String> enabledToolNames;
    private final McpToolCatalog mcpTools;

    public AgentToolRegistry(AgentTools agentTools,
            AgentToolGovernanceProperties properties,
            AgentDurableRuntimeProperties durableProperties, McpToolCatalog mcpTools) {
        this.mcpTools = mcpTools;
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
        if (mcpTools.configuredToolNames().stream().anyMatch(tools::containsKey)) {
            throw new IllegalArgumentException("MCP 工具不能覆盖本地工具");
        }
        this.enabledToolNames = resolveEnabledTools(
                properties.getEnabledTools(), registered, durableProperties);
        LOGGER.info("Registered {} governed Agent tools, enabled={}", tools.size(), enabledToolNames.size());
    }

    private Set<String> resolveEnabledTools(Set<String> configured,
            Map<String, RegisteredTool> registered,
            AgentDurableRuntimeProperties durableProperties) {
        Set<String> registeredNames = new java.util.HashSet<>(registered.keySet());
        registeredNames.addAll(mcpTools.configuredToolNames());
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
        RegisteredTool local = tools.get(name);
        return local == null ? mcpTools.find(name) : Optional.of(local);
    }

    public List<ToolSpecification> specifications() {
        return registeredToolNames().stream().sorted().map(this::find).flatMap(Optional::stream)
                .map(RegisteredTool::specification).toList();
    }

    public Set<String> registeredToolNames() {
        Set<String> names = new java.util.HashSet<>(tools.keySet());
        names.addAll(mcpTools.configuredToolNames());
        return Set.copyOf(names);
    }

    /** 远端服务在能力发现和实际执行时使用同一租户范围。 */
    public boolean isVisibleToTenant(String name, String tenantId) {
        return tools.containsKey(name) || mcpTools.allowsTenant(name, tenantId);
    }

    /** 标记远端能力，供目录展示准确的 externalAccess 风险信息。 */
    public boolean isExternal(String name) { return mcpTools.configuredToolNames().contains(name); }

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
