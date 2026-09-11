package com.ai.agent.outcome;

/**
 * 一条可复现的 Agent 任务成功标准。
 *
 * @param criterionId 标准唯一编号
 * @param type 确定性评估类型
 * @param expectedValue 期望值
 * @param required 是否为整体成功的必选标准，省略时默认为 true
 * @author data-agent
 */
public record AgentSuccessCriterion(
        String criterionId,
        CriterionType type,
        String expectedValue,
        Boolean required) {

    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_EXPECTED_VALUE_LENGTH = 2_000;

    public AgentSuccessCriterion {
        criterionId = normalizeRequired(criterionId, "成功标准 ID", MAX_ID_LENGTH);
        if (type == null) {
            throw new IllegalArgumentException("成功标准类型不能为空");
        }
        expectedValue = normalizeRequired(expectedValue, "成功标准期望值", MAX_EXPECTED_VALUE_LENGTH);
        required = required == null ? Boolean.TRUE : required;
    }

    private static String normalizeRequired(String value, String field, int maximumLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + "不能超过 " + maximumLength + " 个字符");
        }
        return normalized;
    }

    /**
     * 第一版支持的确定性标准类型。
     *
     * @author data-agent
     */
    public enum CriterionType {
        ANSWER_CONTAINS,
        JSON_FIELD_EQUALS,
        TOOL_CALLED,
        APPROVAL_STATUS,
        RUN_STATUS
    }
}
