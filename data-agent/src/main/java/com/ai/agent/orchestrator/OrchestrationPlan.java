package com.ai.agent.orchestrator;

import java.util.ArrayList;
import java.util.List;

/**
 * 编排专家协作的执行计划。
 *
 * @param planId 计划编号
 * @param originalQuery 原始用户请求
 * @param tasks 计划任务
 * @param executionPhases 执行阶段
 * @param sharedContextKeys 专家间共享的上下文键
 * @param createdAtMillis 创建时间戳，单位毫秒
 * @author data-agent
 */
public record OrchestrationPlan(String planId,
        String originalQuery,
        List<OrchestrationTask> tasks,
        List<ExecutionPhase> executionPhases,
        List<String> sharedContextKeys,
        long createdAtMillis) {

    public OrchestrationPlan {
        planId = planId == null ? "plan" : planId;
        originalQuery = originalQuery == null ? "" : originalQuery;
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        executionPhases = executionPhases == null ? List.of() : List.copyOf(executionPhases);
        sharedContextKeys = sharedContextKeys == null ? List.of() : List.copyOf(sharedContextKeys);
    }

    /**
     * 判断计划是否包含任务。
     *
     * @return 包含任务返回 true
     */
    public boolean hasTasks() {
        return !tasks.isEmpty();
    }

    /**
     * 按任务编号提取计划中的任务。
     *
     * @param taskIds 任务编号列表
     * @return 按输入顺序返回的任务列表
     */
    public List<OrchestrationTask> tasksByIds(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty() || tasks.isEmpty()) {
            return List.of();
        }
        List<OrchestrationTask> selectedTasks = new ArrayList<>();
        for (String taskId : taskIds) {
            for (OrchestrationTask task : tasks) {
                if (task.taskId().equals(taskId)) {
                    selectedTasks.add(task);
                    break;
                }
            }
        }
        return List.copyOf(selectedTasks);
    }

    /**
     * 将计划格式化为可见思考内容。
     *
     * @return 思考内容
     */
    public String toThinkingContent() {
        StringBuilder builder = new StringBuilder();
        builder.append("计划ID: ").append(planId).append('\n');
        if (!originalQuery.isBlank()) {
            builder.append("原始请求: ").append(originalQuery).append('\n');
        }
        if (!sharedContextKeys.isEmpty()) {
            builder.append("共享上下文键: ").append(String.join(", ", sharedContextKeys)).append('\n');
        }
        for (int i = 0; i < tasks.size(); i++) {
            OrchestrationTask task = tasks.get(i);
            builder.append(i + 1).append(". ").append(task.description());
            if (!task.specialistName().isBlank()) {
                builder.append(" (专家: ").append(task.specialistName()).append(')');
            }
            if (!task.inputFrom().isEmpty()) {
                builder.append(" [依赖: ").append(String.join(", ", task.inputFrom())).append(']');
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }
}
