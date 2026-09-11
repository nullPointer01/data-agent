package com.ai.agent.runtime.planning;

import com.ai.agent.runtime.AgentExecutionMode;

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
 * @param ragRequired 是否需要个人知识检索
 * @param memoryRequired 是否需要构建记忆上下文
 * @param historyRequired 是否依赖已有会话语义
 * @param candidateTools 规划器建议暴露的候选工具
 * @param toolSelectionFallback 是否因无法可靠缩小工具范围而使用能力绑定兜底
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
        boolean ragRequired,
        boolean memoryRequired,
        boolean historyRequired,
        Set<String> candidateTools,
        boolean toolSelectionFallback,
        AgentToolChoice initialToolChoice,
        double confidence,
        String matchedRule,
        String reason,
        String localResponse) {

    public static final String CURRENT_VERSION = "request-planner-v2";

    public RequestExecutionPlan {
        plannerVersion = textOrDefault(plannerVersion, CURRENT_VERSION);
        intent = intent == null ? RequestIntent.GENERAL_CONVERSATION : intent;
        mode = mode == null ? AgentExecutionMode.CHAT : mode;
        candidateTools = immutableSet(candidateTools);
        initialToolChoice = initialToolChoice == null ? AgentToolChoice.AUTO : initialToolChoice;
        confidence = Math.max(0.0D, Math.min(1.0D, confidence));
        matchedRule = textOrDefault(matchedRule, "default-chat");
        reason = textOrDefault(reason, "未命中专用执行规则，使用普通对话");
        localResponse = localResponse == null ? "" : localResponse;
        if (!modelRequired && localResponse.isBlank()) {
            throw new IllegalArgumentException("本地执行计划必须提供响应内容");
        }
        if (mode == AgentExecutionMode.CHAT && !candidateTools.isEmpty()) {
            throw new IllegalArgumentException("Chat 执行计划不能暴露工具");
        }
        if (initialToolChoice == AgentToolChoice.REQUIRED
                && (mode == AgentExecutionMode.CHAT || candidateTools.isEmpty() || toolSelectionFallback)) {
            throw new IllegalArgumentException("强制工具调用必须使用精确的非空候选工具集");
        }
    }

    /**
     * 为命令和显式 Skill 路线提供不会被配置 Agent 消费的安全占位计划。
     */
    public static RequestExecutionPlan explicitRoute(String reason) {
        return new RequestExecutionPlan(
                CURRENT_VERSION,
                RequestIntent.EXPLICIT_ROUTE,
                AgentExecutionMode.CHAT,
                true,
                false,
                false,
                false,
                Set.of(),
                false,
                AgentToolChoice.AUTO,
                1.0D,
                "explicit-route",
                reason,
                "");
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
        evidence.put("ragRequired", ragRequired);
        evidence.put("memoryRequired", memoryRequired);
        evidence.put("historyRequired", historyRequired);
        evidence.put("candidateTools", candidateTools);
        evidence.put("candidateToolCount", candidateTools.size());
        evidence.put("toolSelectionFallback", toolSelectionFallback);
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
                + "，知识库=" + yesNo(ragRequired)
                + "，记忆=" + yesNo(memoryRequired)
                + "，候选工具=" + (toolSelectionFallback ? "绑定能力兜底" : candidateTools.size() + " 个")
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
