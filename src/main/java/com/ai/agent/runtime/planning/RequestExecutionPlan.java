package com.ai.agent.runtime.planning;

import com.ai.agent.runtime.AgentExecutionMode;
import com.ai.agent.tool.AgentTools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 模型调用前固化的一次请求执行计划。
 *
 * <p>计划只表达资源需求和候选能力，不能绕过能力绑定、RBAC、风险策略或审批管道。</p>
 *
 * @param plannerVersion 规划器版本
 * @param intent 用户目标分类
 * @param mode 执行模式
 * @param modelRequired 是否需要调用模型
 * @param contextPlan 上下文来源的三态使用计划
 * @param planningEvidence 请求规划阶段的安全观测证据
 * @param candidateTools 规划器建议暴露的候选工具
 * @param initialToolChoice 首轮模型调用的工具选择约束
 * @param confidence 规划置信度
 * @param matchedRule 命中的稳定规则编号
 * @param reason 可展示的操作原因，不包含隐藏推理
 * @param localResponse 不调用模型时的本地响应，不进入运行证据
 * @author data-agent
 */
public record RequestExecutionPlan(
        String plannerVersion,
        RequestIntent intent,
        AgentExecutionMode mode,
        boolean modelRequired,
        RequestContextPlan contextPlan,
        RequestPlanningEvidence planningEvidence,
        Set<String> candidateTools,
        AgentToolChoice initialToolChoice,
        double confidence,
        String matchedRule,
        String reason,
        String localResponse) {

    public static final String CURRENT_VERSION = "request-planner-v7";

    public RequestExecutionPlan {
        plannerVersion = textOrDefault(plannerVersion, CURRENT_VERSION);
        intent = intent == null ? RequestIntent.GENERAL_CONVERSATION : intent;
        mode = mode == null ? AgentExecutionMode.CHAT : mode;
        contextPlan = contextPlan == null ? RequestContextPlan.none() : contextPlan;
        planningEvidence = planningEvidence == null
                ? RequestPlanningEvidence.deterministic() : planningEvidence;
        candidateTools = immutableSet(candidateTools);
        initialToolChoice = initialToolChoice == null ? AgentToolChoice.AUTO : initialToolChoice;
        if (!Double.isFinite(confidence) || confidence < 0D || confidence > 1D) {
            throw new IllegalArgumentException("规划置信度必须在 0 到 1 之间");
        }
        matchedRule = textOrDefault(matchedRule, "semantic-router-fallback");
        reason = textOrDefault(reason, "语义规划不可用，使用保守回答");
        localResponse = localResponse == null ? "" : localResponse;
        if (!modelRequired && localResponse.isBlank()) {
            throw new IllegalArgumentException("本地执行计划必须提供响应内容");
        }
        if (mode == AgentExecutionMode.CHAT && !candidateTools.isEmpty()) {
            throw new IllegalArgumentException("Chat 执行计划不能暴露工具");
        }
        if (candidateTools.size() > AgentToolCandidateSelector.MAX_CANDIDATES) {
            throw new IllegalArgumentException("计划候选工具超过上限");
        }
        if (mode != AgentExecutionMode.ORCHESTRATED
                && candidateTools.contains(AgentTools.DELEGATE_TO_AGENT_TOOL)) {
            throw new IllegalArgumentException("只有 Orchestrated 模式可以委派子 Agent");
        }
        validateRetrieval(contextPlan.semanticMemory(), candidateTools,
                AgentToolCandidateSelector.SEARCH_MEMORY_TOOL);
        validateRetrieval(contextPlan.knowledgeBase(), candidateTools,
                AgentToolCandidateSelector.SEARCH_KNOWLEDGE_TOOL);
        if (mode == AgentExecutionMode.CHAT
                && (contextPlan.semanticMemory() == ContextRequirement.OPTIONAL
                || contextPlan.knowledgeBase() == ContextRequirement.OPTIONAL)) {
            throw new IllegalArgumentException("Chat 执行计划不能声明按需检索");
        }
        if (initialToolChoice == AgentToolChoice.REQUIRED
                && (mode == AgentExecutionMode.CHAT || candidateTools.isEmpty())) {
            throw new IllegalArgumentException("强制工具调用必须使用精确的非空候选工具集");
        }
    }

    private static void validateRetrieval(ContextRequirement requirement, Set<String> candidates,
            String toolName) {
        if ((requirement == ContextRequirement.OPTIONAL) != candidates.contains(toolName)) {
            throw new IllegalArgumentException("按需检索计划与工具候选不一致: " + toolName);
        }
    }

    /**
     * 返回可持久化和对用户展示的安全规划证据，不包含本地响应正文。
     */
    public Map<String, Object> toEvidence() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("plannerVersion", plannerVersion);
        evidence.put("intent", intent.name());
        evidence.put("mode", mode.name());
        evidence.put("modelRequired", modelRequired);
        evidence.put("contextPlan", contextPlan.toEvidence());
        evidence.put("planning", planningEvidence.toEvidence());
        evidence.put("candidateTools", candidateTools);
        evidence.put("candidateToolCount", candidateTools.size());
        evidence.put("initialToolChoice", initialToolChoice.name());
        evidence.put("confidence", confidence);
        evidence.put("matchedRule", matchedRule);
        evidence.put("reason", reason);
        return Collections.unmodifiableMap(evidence);
    }

    /**
     * 生成前端时间线使用的简短执行说明。
     */
    public String summary() {
        String resourceSummary = "模型=" + yesNo(modelRequired)
                + "，知识库=" + contextPlan.knowledgeBase().name()
                + "，语义记忆=" + contextPlan.semanticMemory().name()
                + "，用户画像=" + contextPlan.userProfile().name()
                + "，会话历史=" + contextPlan.conversationHistory().name()
                + "，候选工具=" + candidateTools.size() + " 个"
                + "，首轮工具选择=" + initialToolChoice.name();
        return intent.name() + " -> " + mode.name() + "；" + resourceSummary + "；原因：" + reason;
    }

    private static Set<String> immutableSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String yesNo(boolean value) {
        return value ? "是" : "否";
    }
}
