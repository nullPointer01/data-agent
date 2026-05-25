package com.ai.agent.orchestrator;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.ai.agent.TaskComplexity;

/**
 * ReAct 循环开始前的执行计划。
 *
 * <p>由 {@link TaskPlanner} 根据请求上下文和分类结果构建。包含有序步骤列表和
 * 总体复杂度评估。</p>
 *
 * @author data-agent
 */
public class ExecutionPlan {

    private final TaskComplexity complexity;
    private final String reason;
    private final List<ExecutionPlanStep> steps;

    /**
     * 构造执行计划。
     *
     * @param complexity 任务复杂度
     * @param reason 分类原因
     * @param steps 有序步骤列表
     */
    public ExecutionPlan(TaskComplexity complexity, String reason, List<ExecutionPlanStep> steps) {
        this.complexity = complexity == null ? TaskComplexity.COMPLEX : complexity;
        this.reason = reason == null ? "" : reason;
        this.steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /**
     * 返回任务复杂度。
     *
     * @return 复杂度枚举
     */
    public TaskComplexity complexity() {
        return complexity;
    }

    /**
     * 返回分类原因。
     *
     * @return 原因说明
     */
    public String reason() {
        return reason;
    }

    /**
     * 返回步骤列表。
     *
     * @return 不可变步骤列表
     */
    public List<ExecutionPlanStep> steps() {
        return steps;
    }

    /**
     * 判断计划是否包含步骤。
     *
     * @return 包含步骤返回 true
     */
    public boolean hasSteps() {
        return !steps.isEmpty();
    }

    /**
     * 按阶段分组返回并行执行组。
     *
     * @return 阶段编号到步骤列表的映射
     */
    public Map<Integer, List<ExecutionPlanStep>> getParallelGroups() {
        return steps.stream().collect(Collectors.groupingBy(ExecutionPlanStep::phase));
    }

    /**
     * 将计划格式化为可见思考内容。
     *
     * @return 思考内容
     */
    public String toThinkingContent() {
        StringBuilder builder = new StringBuilder();
        builder.append("复杂度: ").append(complexity).append('\n');
        if (!reason.isBlank()) {
            builder.append("原因: ").append(reason).append('\n');
        }
        for (int i = 0; i < steps.size(); i++) {
            ExecutionPlanStep step = steps.get(i);
            builder.append(i + 1).append(". [").append(step.id()).append("] ").append(step.objective());
            if (!step.suggestedTool().isBlank()) {
                builder.append(" (工具: ").append(step.suggestedTool()).append(')');
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    /**
     * 将计划格式化为注入 ReAct 系统提示的执行指令。
     *
     * @return 提示内容
     */
    public String toPromptContent() {
        if (steps.isEmpty()) {
            return "";
        }
        Map<Integer, List<ExecutionPlanStep>> groups = getParallelGroups();
        StringBuilder builder = new StringBuilder();
        builder.append("请按照以下计划执行：\n");
        for (Map.Entry<Integer, List<ExecutionPlanStep>> entry : groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            builder.append("阶段 ").append(entry.getKey()).append(":\n");
            for (ExecutionPlanStep step : entry.getValue()) {
                builder.append("  - ").append(step.objective());
                if (!step.suggestedTool().isBlank()) {
                    builder.append(" (建议工具: ").append(step.suggestedTool()).append(')');
                }
                builder.append('\n');
            }
        }
        return builder.toString().trim();
    }
}
