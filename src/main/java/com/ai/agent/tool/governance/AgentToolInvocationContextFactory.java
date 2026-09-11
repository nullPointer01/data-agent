package com.ai.agent.tool.governance;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 只基于服务端 Registry 和实际模型规格创建工具调用白名单。
 *
 * @author data-agent
 */
@Component
public class AgentToolInvocationContextFactory {

    public static final Set<String> PLANNER_PRECHECK_TOOLS = Set.of(
            "searchKnowledge", "listDataSources", "searchMemory");

    private final AgentToolRegistry registry;

    public AgentToolInvocationContextFactory(AgentToolRegistry registry) {
        this.registry = registry;
    }

    public AgentToolInvocationContext defaultReact() {
        return fromNames("react:default", registry.enabledToolNames());
    }

    public AgentToolInvocationContext profile(String agentId, List<ToolSpecification> specifications) {
        String executorId = agentId == null || agentId.isBlank() ? "react:profile" : "profile:" + agentId;
        return fromSpecifications(executorId, specifications);
    }

    public AgentToolInvocationContext plannerPrecheck() {
        return fromNames("react:precheck", PLANNER_PRECHECK_TOOLS);
    }

    public AgentToolInvocationContext fromSpecifications(String executorId,
            List<ToolSpecification> specifications) {
        Set<String> names = specifications == null ? Set.of() : specifications.stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toUnmodifiableSet());
        return fromNames(executorId, names);
    }

    private AgentToolInvocationContext fromNames(String executorId, Collection<String> requestedNames) {
        Set<String> allowed = requestedNames == null ? Set.of() : requestedNames.stream()
                .filter(registry.enabledToolNames()::contains)
                .collect(Collectors.toUnmodifiableSet());
        return new AgentToolInvocationContext(executorId, allowed);
    }
}
