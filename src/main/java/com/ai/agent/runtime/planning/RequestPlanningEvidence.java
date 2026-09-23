package com.ai.agent.runtime.planning;

import java.util.Map;

/**
 * 请求规划阶段的安全观测证据。
 *
 * <p>顶层 Router 发生在根 Run 建立之前；子 Agent Router 已在根 Run 作用域内，计入共享预算。</p>
 *
 * @param semanticRouterEligible 是否进入语义 Router 路径
 * @param modelRequestAttempted 是否向统一模型服务发起路由请求
 * @param durationMs 规划阶段 Router 耗时
 * @param includedInRunBudget 是否计入根 Run 的模型调用和 Token 预算
 */
public record RequestPlanningEvidence(
        boolean semanticRouterEligible,
        boolean modelRequestAttempted,
        long durationMs,
        boolean includedInRunBudget) {

    public RequestPlanningEvidence {
        durationMs = Math.max(0L, durationMs);
    }

    public static RequestPlanningEvidence deterministic() {
        return new RequestPlanningEvidence(false, false, 0L, false);
    }

    public Map<String, Object> toEvidence() {
        return Map.of(
                "semanticRouterEligible", semanticRouterEligible,
                "modelRequestAttempted", modelRequestAttempted,
                "durationMs", durationMs,
                "includedInRunBudget", includedInRunBudget,
                "budgetScope", includedInRunBudget ? "ROOT_RUN_AND_USER_MODEL_QUOTA" : "USER_MODEL_QUOTA");
    }
}
