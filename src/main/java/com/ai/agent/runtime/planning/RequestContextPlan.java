package com.ai.agent.runtime.planning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次 Agent Run 对各类上下文来源的不可变使用计划。
 *
 * <p>该计划只记录来源级决策和检索查询，不保存历史、记忆或知识正文。</p>
 *
 * @param conversationHistory 当前会话历史要求
 * @param userProfile 稳定用户画像要求
 * @param semanticMemory 跨会话语义记忆要求
 * @param knowledgeBase 个人知识库要求
 * @param retrievalQuery 供知识或记忆检索使用的独立查询
 * @param decisionSource 本次上下文决策来源
 * @author data-agent
 */
public record RequestContextPlan(
        ContextRequirement conversationHistory,
        ContextRequirement userProfile,
        ContextRequirement semanticMemory,
        ContextRequirement knowledgeBase,
        String retrievalQuery,
        DecisionSource decisionSource) {

    public RequestContextPlan {
        conversationHistory = normalize(conversationHistory);
        userProfile = normalize(userProfile);
        semanticMemory = normalize(semanticMemory);
        knowledgeBase = normalize(knowledgeBase);
        retrievalQuery = retrievalQuery == null ? "" : retrievalQuery.trim();
        decisionSource = decisionSource == null ? DecisionSource.FALLBACK : decisionSource;
    }

    /** 返回不使用任何外部上下文的保守计划。 */
    public static RequestContextPlan none() {
        return new RequestContextPlan(ContextRequirement.OFF, ContextRequirement.OFF,
                ContextRequirement.OFF, ContextRequirement.OFF, "", DecisionSource.FALLBACK);
    }

    public boolean shouldPreloadConversationHistory() {
        return conversationHistory.shouldPreload();
    }

    public boolean shouldPreloadUserProfile() {
        return userProfile.shouldPreload();
    }

    public boolean shouldPreloadSemanticMemory() {
        return semanticMemory.shouldPreload();
    }

    public boolean shouldPreloadKnowledge() {
        return knowledgeBase.shouldPreload();
    }

    public boolean allowsSemanticMemoryTool() {
        return semanticMemory.allowsOnDemandRetrieval();
    }

    public boolean allowsKnowledgeTool() {
        return knowledgeBase.allowsOnDemandRetrieval();
    }

    /**
     * 生成不包含上下文正文的可持久化证据。
     *
     * @return 来源要求、决策来源和检索查询
     */
    public Map<String, Object> toEvidence() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("conversationHistory", conversationHistory.name());
        evidence.put("userProfile", userProfile.name());
        evidence.put("semanticMemory", semanticMemory.name());
        evidence.put("knowledgeBase", knowledgeBase.name());
        evidence.put("retrievalQuery", retrievalQuery);
        evidence.put("decisionSource", decisionSource.name());
        return Collections.unmodifiableMap(evidence);
    }

    private static ContextRequirement normalize(ContextRequirement requirement) {
        return requirement == null ? ContextRequirement.OFF : requirement;
    }

    /** 上下文计划的决策来源。 */
    public enum DecisionSource {
        /** 高确定性代码规则。 */
        DETERMINISTIC,
        /** 结构化语义 Router。 */
        SEMANTIC_ROUTER,
        /** Router 不可用或证据不足后的保守降级。 */
        FALLBACK
    }
}
