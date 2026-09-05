package com.ai.agent;

import java.util.List;

/**
 * Orchestrator 路由使用的确定性意图分析结果。
 *
 * @param intent 主意图
 * @param preferredType 该意图推荐的专家类型
 * @param confidence 置信度，范围 0.0 到 1.0
 * @param matchedSignals 命中的关键词或上下文信号
 * @param reason 意图原因
 * @author data-agent
 */
public record IntentAnalysisResult(AgentIntent intent,
        AgentType preferredType,
        double confidence,
        List<String> matchedSignals,
        String reason) {

    private static final double MIN_CONFIDENCE = 0.0D;
    private static final double MAX_CONFIDENCE = 1.0D;

    public IntentAnalysisResult {
        intent = intent == null ? AgentIntent.COMPLEX_REASONING : intent;
        preferredType = preferredType == null ? AgentType.REACT : preferredType;
        confidence = Math.max(MIN_CONFIDENCE, Math.min(MAX_CONFIDENCE, confidence));
        matchedSignals = matchedSignals == null ? List.of() : List.copyOf(matchedSignals);
        reason = reason == null ? "" : reason;
    }

    /**
     * 将意图信息格式化为可见思考内容。
     *
     * @return 意图摘要
     */
    public String toThinkingContent() {
        StringBuilder builder = new StringBuilder();
        builder.append("识别意图: ").append(intent).append('\n');
        builder.append("推荐专家: ").append(preferredType).append('\n');
        builder.append("置信度: ").append(String.format(java.util.Locale.ROOT, "%.2f", confidence)).append('\n');
        if (!matchedSignals.isEmpty()) {
            builder.append("命中信号: ").append(String.join(", ", matchedSignals)).append('\n');
        }
        if (!reason.isBlank()) {
            builder.append("意图原因: ").append(reason);
        }
        return builder.toString().trim();
    }
}
