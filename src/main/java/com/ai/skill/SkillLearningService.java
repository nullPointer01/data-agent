package com.ai.skill;

import com.ai.agent.AgentRunOrigin;
import com.ai.agent.capability.AgentCapabilityScope;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.durable.AgentRunStateRepository;
import com.ai.agent.tool.governance.AgentToolRegistry;
import com.ai.conversation.ConversationAccessGuard;
import com.ai.repository.AgentProfileRepository;
import com.ai.repository.ConversationMessageRepository;
import com.ai.repository.SkillConfigRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.skill.dto.SkillResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 两种学习来源：模型按需提议用户方法、本人从完成的任务提炼经验；均先进入待确认区。 */
@Service
public class SkillLearningService {
    private final SkillDraftGenerator generator;
    private final SkillLearningStore store;
    private final SkillConfigRepository skills;
    private final AgentRunStateRepository runs;
    private final ConversationMessageRepository messages;
    private final ConversationAccessGuard conversations;
    private final SecurityContextHelper security;
    private final AgentProfileRepository profiles;
    // Registry 注册 AgentTools，而 Tool 门面调用本服务；延迟读取目录避免构造期依赖环。
    private final ObjectProvider<AgentToolRegistry> registry;
    private final ObjectProvider<com.ai.agent.capability.AgentCapabilityService> capabilities;

    public SkillLearningService(SkillDraftGenerator generator, SkillLearningStore store, SkillConfigRepository skills,
            AgentRunStateRepository runs, ConversationMessageRepository messages, ConversationAccessGuard conversations,
            SecurityContextHelper security, AgentProfileRepository profiles, ObjectProvider<AgentToolRegistry> registry,
            ObjectProvider<com.ai.agent.capability.AgentCapabilityService> capabilities) {
        this.generator = generator; this.store = store; this.skills = skills; this.runs = runs;
        this.messages = messages; this.conversations = conversations; this.security = security;
        this.profiles = profiles; this.registry = registry;
        this.capabilities = capabilities;
    }

    /** 模型可以主动提议，但只接受本轮用户原话；委派文本不能充当用户方法证据。 */
    public SkillResponse propose(String evidence, SkillLearningSource source) {
        var run = AgentRunScope.requireCurrent();
        run.control().ensureActive();
        if (run.executionContext().getOrigin() == AgentRunOrigin.DELEGATED) {
            throw new SecurityException("委派文本不能创建用户学习候选");
        }
        String question = run.executionContext().getRequest().getQuestion();
        if (evidence == null || evidence.isBlank() || !question.contains(evidence)) {
            throw new IllegalArgumentException("技能学习需要本轮用户原文证据");
        }
        var existing = skills.findByLearningRun(run.tenantId(), run.userId(), run.runId());
        if (existing.isPresent()) return SkillResponse.from(existing.get());
        var binding = AgentCapabilityScope.current().orElseThrow();
        if (source == null) throw new IllegalArgumentException("缺少技能学习来源类型");
        String experience = "";
        if (source == SkillLearningSource.EXECUTED_WORKFLOW) {
            // Journal 是实际执行事实；不接收模型传入的成功轨迹或结果，也不把仅提议的工具当作已执行。
            var completed = run.toolJournal().snapshot().stream()
                    .filter(entry -> entry.authorized() && entry.status() == com.ai.agent.tool.governance.AgentToolExecutionStatus.SUCCESS)
                    .filter(entry -> binding.toolNames().contains(entry.toolName()))
                    .filter(entry -> !Set.of("proposeSkill", "activate_skill", "read_skill_resource", "delegateToAgent", "askUserForInfo").contains(entry.toolName()))
                    .map(com.ai.agent.tool.governance.AgentToolExecutionRecord::toolName).toList();
            if (completed.stream().distinct().count() < 2 || run.toolJournal().overflowCount() > 0) {
                throw new IllegalArgumentException("尚无完整的多工具成功流程可提炼");
            }
            experience = "服务端确认本次已成功执行的工具，按发生顺序：" + completed;
        }
        var draft = generator.generate(question, experience, source.name(), toolDescriptions(binding.toolNames()),
                modelId(run.agentId(), run.executionContext().getRequest().getModelId()), true);
        if (!question.contains(draft.evidence())) throw new IllegalArgumentException("方法证据必须来自用户原文");
        run.control().ensureActive();
        return store.save(run.runId(), draft);
    }

    /** 只读取本人已完成的持久 Run 和对话，不接受浏览器传入的伪造执行结果。 */
    public SkillResponse fromRun(String runId) {
        String tenant = security.getCurrentTenantId(), user = security.getCurrentUserId();
        var run = runs.findByRunIdAndTenantIdAndUserId(runId, tenant, user)
                .filter(value -> value.getStatus() == AgentRunStatus.COMPLETED)
                .orElseThrow(() -> new IllegalArgumentException("任务尚未完成或无权限"));
        conversations.requireReadable(run.getSessionId(), tenant, user);
        var existing = skills.findByLearningRun(tenant, user, runId);
        if (existing.isPresent()) return SkillResponse.from(existing.get());
        var question = messages.findFirstBySessionIdAndRunIdAndRoleOrderByCreatedAtAsc(run.getSessionId(), runId, "user")
                .orElseThrow(() -> new IllegalArgumentException("缺少任务原始问题"));
        var answer = messages.findFirstBySessionIdAndRunIdAndRoleOrderByCreatedAtAsc(run.getSessionId(), runId, "assistant")
                .orElseThrow(() -> new IllegalArgumentException("缺少任务结果"));
        Set<String> available = profiles.findByAgentIdAndTenantId(run.getAgentId(), tenant)
                .filter(com.ai.model.AgentProfile::isEnabled)
                .map(profile -> capabilities.getObject().resolveRuntimeBindings(profile,
                        com.ai.agent.runtime.AgentExecutionMode.REACT).toolNames()).orElse(Set.of());
        var draft = generator.generate(question.getContent(), answer.getContent(), "TASK_EXPERIENCE",
                toolDescriptions(available), modelId(run.getAgentId(), null), false);
        return store.save(runId, draft);
    }

    /** 手工生成同样只返回草稿；管理员检查、编辑并创建后才成为共享技能。 */
    public SkillDraftGenerator.Draft manual(String data, String description) {
        return generator.generate(description == null ? "" : description, data == null ? "" : data,
                "MANUAL", toolDescriptions(registry.getObject().enabledToolNames()), null, false);
    }

    private List<Map<String, String>> toolDescriptions(Set<String> names) {
        var tools = registry.getObject();
        return names.stream().filter(name -> !Set.of("activate_skill", "read_skill_resource", "delegateToAgent", "proposeSkill").contains(name))
                .filter(name -> tools.isVisibleToTenant(name, security.getCurrentTenantId()))
                .sorted().map(tools::find).flatMap(java.util.Optional::stream)
                .map(tool -> Map.of("name", tool.specification().name(), "description", tool.specification().description())).toList();
    }

    private String modelId(String agentId, String requested) {
        if (requested != null && !requested.isBlank()) return requested;
        return profiles.findByAgentIdAndTenantId(agentId, security.getCurrentTenantId())
                .map(com.ai.model.AgentProfile::getModelId).orElse(null);
    }
}
