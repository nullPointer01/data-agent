package com.ai.agent.runtime.planning;

import com.ai.agent.AgentExecutionContext;
import com.ai.agent.capability.AgentCapabilityBindingSnapshot;
import com.ai.agent.capability.AgentCapabilityDescriptor;
import com.ai.agent.capability.AgentCapabilityService;
import com.ai.agent.runtime.AgentExecutionMode;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.tool.AgentTools;
import com.ai.model.AgentProfile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 先根据结构化状态确定能力边界，再通过语义规划生成不可变执行计划。
 * 业务意图和工具用途从模型及能力描述获得，规划器不维护业务词表。
 */
@Component
public class PersonalAgentRequestPlanner {

    private static final Set<String> CAPABILITY_QUESTIONS = Set.of(
            "你是谁", "你能做什么", "你能干什么", "你有哪些能力");
    private final AgentCapabilityService capabilityService;
    private final AgentToolCandidateSelector toolSelector;
    private final SemanticRequestRouter semanticRouter;

    public PersonalAgentRequestPlanner(AgentCapabilityService capabilityService,
            AgentToolCandidateSelector toolSelector, SemanticRequestRouter semanticRouter) {
        this.capabilityService = capabilityService;
        this.toolSelector = toolSelector;
        this.semanticRouter = semanticRouter;
    }

    /** 为本次请求生成与当前权限、模式及会话状态一致的计划。 */
    public RequestExecutionPlan plan(AgentProfile profile, AgentExecutionContext executionContext) {
        if (profile == null || executionContext == null || executionContext.getRequest() == null) {
            throw new IllegalArgumentException("请求规划缺少 Agent 或执行上下文");
        }
        String question = executionContext.getRequest().getQuestion();
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("请求问题不能为空");
        }
        AgentExecutionMode fixedMode = profile.isAutoMode() || profile.getExecutionMode() == null
                || profile.getExecutionMode().isBlank()
                ? null : AgentExecutionMode.fromCode(profile.getExecutionMode());
        AgentCapabilityBindingSnapshot snapshot = capabilityService.resolveRuntimeBindings(
                profile, fixedMode == null ? AgentExecutionMode.ORCHESTRATED : fixedMode);
        List<AgentCapabilityDescriptor> directory = capabilityService.runtimeDirectory(profile).capabilities();
        List<SemanticRequestRouter.ToolCandidate> catalog = toolSelector.catalog(snapshot, directory);
        boolean filePresent = executionContext.getRequest().hasFile()
                || (executionContext.getFileContent() != null && !executionContext.getFileContent().isBlank());
        // 只匹配完整的独立问句，避免“你能做什么，然后帮我……”被本地回答截走。
        if (!filePresent && CAPABILITY_QUESTIONS.contains(question.trim().replaceAll("[\\s。！？!?]+$", ""))) {
            return localPlan(profile, catalog);
        }
        boolean historyPresent = executionContext.getSession() != null
                && executionContext.getSession().getHistory() != null
                && !executionContext.getSession().getHistory().isEmpty();
        String history = historyPresent ? executionContext.getSession().buildRecentHistory() : "";
        SemanticRequestRouter.Result result = semanticRouter.route(new SemanticRequestRouter.Input(
                question, history, profile.getModelId(),
                description(profile), filePresent, catalog));
        SemanticRequestRouter.Decision decision = result.decision().orElse(null);
        Set<String> candidates = Set.of();
        if (decision != null) {
            try {
                candidates = toolSelector.select(decision, catalog);
            } catch (IllegalArgumentException invalidSelection) {
                // 组合校验失败与模型合同失败使用相同降级，禁止扩大成全部绑定能力。
                decision = null;
            }
        }
        AgentExecutionMode mode = fixedMode != null ? fixedMode
                : candidates.contains(AgentTools.DELEGATE_TO_AGENT_TOOL) ? AgentExecutionMode.ORCHESTRATED
                : candidates.isEmpty() ? AgentExecutionMode.CHAT : AgentExecutionMode.REACT;
        if (mode == AgentExecutionMode.CHAT) {
            candidates = Set.of();
        }
        RequestContextPlan contextPlan = new RequestContextPlan(
                historyPresent ? ContextRequirement.REQUIRED : ContextRequirement.OFF,
                ContextRequirement.REQUIRED,
                usableRequirement(decision == null ? ContextRequirement.OFF : decision.semanticMemory(),
                        candidates, AgentToolCandidateSelector.SEARCH_MEMORY_TOOL),
                usableRequirement(decision == null ? ContextRequirement.OFF : decision.knowledgeBase(),
                        candidates, AgentToolCandidateSelector.SEARCH_KNOWLEDGE_TOOL),
                decision == null ? question : decision.retrievalQuery(),
                decision == null ? RequestContextPlan.DecisionSource.FALLBACK
                        : RequestContextPlan.DecisionSource.SEMANTIC_ROUTER);
        return new RequestExecutionPlan(
                RequestExecutionPlan.CURRENT_VERSION,
                decision == null ? RequestIntent.GENERAL_CONVERSATION : decision.intent(),
                mode, true, contextPlan,
                new RequestPlanningEvidence(true, result.modelRequestAttempted(), result.durationMs(),
                        AgentRunScope.current().isPresent()),
                candidates, AgentToolChoice.AUTO,
                decision == null ? 0D : decision.confidence(),
                decision == null ? "semantic-router-fallback" : "semantic-router",
                decision == null
                        ? "语义规划不可用或输出未通过校验；保留会话、画像和已加载文件，本次不开放工具或额外检索"
                        : decision.reason(),
                "");
    }

    private ContextRequirement usableRequirement(ContextRequirement requirement,
            Set<String> candidates, String retrievalTool) {
        if (requirement == ContextRequirement.OPTIONAL && !candidates.contains(retrievalTool)) {
            return ContextRequirement.OFF;
        }
        return requirement;
    }

    private String description(AgentProfile profile) {
        return "名称：" + (profile.getName() == null ? "" : profile.getName())
                + "\n用途：" + (profile.getDescription() == null ? "" : profile.getDescription())
                + "\n角色说明：" + (profile.getSystemPrompt() == null ? "" : profile.getSystemPrompt());
    }

    private RequestExecutionPlan localPlan(AgentProfile profile,
            List<SemanticRequestRouter.ToolCandidate> catalog) {
        StringBuilder answer = new StringBuilder("我是 ")
                .append(profile.getName() == null ? "你的个人 Agent" : profile.getName())
                .append("。可以进行对话，并根据需要参考你的个人知识和记忆。\n");
        if (catalog.isEmpty()) {
            answer.append("当前配置没有开放工具、技能或专家委派。");
        } else {
            answer.append("\n当前可用能力：\n");
            catalog.stream()
                    .filter(tool -> !tool.name().equals(AgentTools.LIST_AVAILABLE_SKILLS_TOOL))
                    .forEach(tool -> {
                        answer.append("- ").append(tool.description()).append('\n');
                        tool.targets().forEach(target -> answer.append("  ")
                                .append(target.name()).append("：").append(target.description()).append('\n'));
                    });
            answer.append("\n工具执行遵守权限和预算；需要审批的动作会先暂停等待批准。");
        }
        return new RequestExecutionPlan(RequestExecutionPlan.CURRENT_VERSION,
                RequestIntent.CAPABILITY_INTRODUCTION, AgentExecutionMode.CHAT, false,
                RequestContextPlan.none(), RequestPlanningEvidence.deterministic(),
                Set.of(), AgentToolChoice.AUTO, 1D, "exact-capability-introduction",
                "完整请求仅询问当前身份或可用能力", answer.toString());
    }
}
