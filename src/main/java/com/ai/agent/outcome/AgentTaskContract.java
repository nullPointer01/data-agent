package com.ai.agent.outcome;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 一次 Agent Run 的不可变任务目标与成功标准。
 *
 * @param goal 任务目标
 * @param criteria 成功标准
 * @author data-agent
 */
public record AgentTaskContract(String goal, List<AgentSuccessCriterion> criteria) {

    private static final int MAX_GOAL_LENGTH = 2_000;
    private static final int MAX_CRITERIA = 20;

    public AgentTaskContract {
        goal = normalizeGoal(goal);
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("任务合同至少需要一条成功标准");
        }
        if (criteria.size() > MAX_CRITERIA) {
            throw new IllegalArgumentException("任务合同最多支持 " + MAX_CRITERIA + " 条成功标准");
        }
        Set<String> ids = new HashSet<>();
        for (AgentSuccessCriterion criterion : criteria) {
            if (criterion == null) {
                throw new IllegalArgumentException("成功标准不能为空");
            }
            if (!ids.add(criterion.criterionId())) {
                throw new IllegalArgumentException("成功标准 ID 不能重复: " + criterion.criterionId());
            }
        }
    }

    private static String normalizeGoal(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("任务目标不能为空");
        }
        if (normalized.length() > MAX_GOAL_LENGTH) {
            throw new IllegalArgumentException("任务目标不能超过 " + MAX_GOAL_LENGTH + " 个字符");
        }
        return normalized;
    }
}
