package com.ai.agent.runtime.planning;

import com.ai.agent.capability.AgentCapabilityBindingSnapshot;
import com.ai.agent.capability.AgentCapabilityDescriptor;
import com.ai.agent.tool.AgentTools;
import com.ai.skill.SkillCatalog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 从运行时能力元数据构建规划目录，并校验模型选择，不维护业务关键词或领域工具映射。
 */
@Component
public class AgentToolCandidateSelector {

    public static final int MAX_CANDIDATES = 5;
    public static final String SEARCH_MEMORY_TOOL = "searchMemory";
    public static final String SEARCH_KNOWLEDGE_TOOL = "searchKnowledge";

    /** 只投影已绑定、已授权、当前模式可用的能力，避免把平台目录泄露给 Router。 */
    public List<SemanticRequestRouter.ToolCandidate> catalog(AgentCapabilityBindingSnapshot snapshot,
            List<AgentCapabilityDescriptor> directory) {
        List<SemanticRequestRouter.ToolCandidate> result = new ArrayList<>();
        List<SemanticRequestRouter.CapabilitySummary> skills = new ArrayList<>();
        List<SemanticRequestRouter.CapabilitySummary> agents = new ArrayList<>();
        for (AgentCapabilityDescriptor descriptor : directory) {
            if (!descriptor.availability().available()) {
                continue;
            }
            String id = descriptor.type().sourceId(descriptor.identity());
            String description = shorten(descriptor.description());
            switch (descriptor.type()) {
                case TOOL -> {
                    if (snapshot.allowsTool(id)) {
                        result.add(new SemanticRequestRouter.ToolCandidate(id, description,
                                descriptor.risk().readOnly(), List.of()));
                    }
                }
                case SKILL -> {
                    if (snapshot.allowsSkill(id)) {
                        skills.add(new SemanticRequestRouter.CapabilitySummary(id,
                                shorten(descriptor.name()), description));
                    }
                }
                case SUB_AGENT -> {
                    if (snapshot.allowsSubAgent(id)) {
                        agents.add(new SemanticRequestRouter.CapabilitySummary(id,
                                shorten(descriptor.name()), description));
                    }
                }
            }
        }
        if (!skills.isEmpty()) {
            result.add(new SemanticRequestRouter.ToolCandidate(SkillCatalog.ACTIVATE_TOOL,
                    "按需加载已绑定技能的操作指引，由当前 Agent 继续处理任务，不会独立调用模型", true, skills));
            result.add(new SemanticRequestRouter.ToolCandidate(SkillCatalog.READ_RESOURCE_TOOL,
                    "读取技能指令引用的步骤资料；应先激活相应技能", true, skills));
        }
        if (!agents.isEmpty()) {
            result.add(new SemanticRequestRouter.ToolCandidate(AgentTools.DELEGATE_TO_AGENT_TOOL,
                    "任务适合某个已绑定专家的职责时委派；单个任务也可委派，不要求多步骤", false, agents));
        }
        return result.stream().sorted(Comparator.comparing(SemanticRequestRouter.ToolCandidate::name)).toList();
    }

    /** 校验工具名并补齐按需检索与 Skill adapter；无法兑现的候选绝不回退到全部绑定。 */
    public Set<String> select(SemanticRequestRouter.Decision decision,
            List<SemanticRequestRouter.ToolCandidate> catalog) {
        Set<String> allowed = catalog.stream().map(SemanticRequestRouter.ToolCandidate::name)
                .collect(Collectors.toUnmodifiableSet());
        if (!allowed.containsAll(decision.candidateTools())) {
            throw new IllegalArgumentException("Router 选择了目录外工具");
        }
        Set<String> selected = new LinkedHashSet<>(decision.candidateTools());
        reconcileRetrieval(selected, allowed, SEARCH_MEMORY_TOOL, decision.semanticMemory());
        reconcileRetrieval(selected, allowed, SEARCH_KNOWLEDGE_TOOL, decision.knowledgeBase());
        if (selected.contains(SkillCatalog.ACTIVATE_TOOL) || selected.contains(SkillCatalog.READ_RESOURCE_TOOL)) {
            if (!allowed.containsAll(Set.of(SkillCatalog.ACTIVATE_TOOL, SkillCatalog.READ_RESOURCE_TOOL))) {
                throw new IllegalArgumentException("Skill 缺少激活或资料读取工具");
            }
            selected.add(SkillCatalog.ACTIVATE_TOOL);
            selected.add(SkillCatalog.READ_RESOURCE_TOOL);
        }
        if (selected.size() > MAX_CANDIDATES) {
            // 不截断依赖链，否则可能只剩执行工具而丢掉发现或检索工具。
            throw new IllegalArgumentException("Router 候选能力超过上限");
        }
        return Set.copyOf(selected);
    }

    private void reconcileRetrieval(Set<String> selected, Set<String> allowed, String name,
            ContextRequirement requirement) {
        if (requirement == ContextRequirement.OPTIONAL && allowed.contains(name)) {
            selected.add(name);
        } else {
            selected.remove(name);
        }
    }

    private String shorten(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 400 ? text : text.substring(0, 400);
    }
}
