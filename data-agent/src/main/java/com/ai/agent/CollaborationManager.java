package com.ai.agent;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.ai.agent.orchestrator.OrchestrationTask;
import com.ai.agent.specialist.SpecialistResult;
import com.ai.agent.specialist.SpecialistTask;

/**
 * 协作管理器，负责管理多专家协作的共享上下文、依赖注入和阻塞依赖检测。
 *
 * <p>核心职责：
 * <ul>
 *     <li>将各专家任务结果按计划顺序合并到共享上下文</li>
 *     <li>为下游任务注入其声明依赖的上游结果</li>
 *     <li>检测因上游失败或缺失而被阻塞的依赖</li>
 * </ul>
 *
 * @author data-agent
 */
@Service
public class CollaborationManager {

    /** 共享上下文中的任务执行顺序键 */
    public static final String KEY_TASK_ORDER = "taskOrder";
    /** 共享上下文中的协作汇总键 */
    public static final String KEY_COLLABORATION_SUMMARY = "collaborationSummary";
    /** 共享上下文中的质量评估键 */
    public static final String KEY_QUALITY = "quality";
    /** 共享上下文中的依赖图键 */
    public static final String KEY_DEPENDENCY_GRAPH = "dependencyGraph";
    /** 注入到专家任务中的已解析依赖键 */
    public static final String KEY_DEPENDENCIES = "dependencies";

    /**
     * 将所有专家执行结果按计划顺序合并到共享上下文。
     *
     * <p>处理流程：
     * <ol>
     *     <li>按任务计划顺序对结果排序</li>
     *     <li>将每个结果的 outputContext 扁平化到共享上下文顶层</li>
     *     <li>按 taskId 存储结构化任务结果</li>
     *     <li>生成协作汇总、质量评估和依赖图</li>
     * </ol>
     *
     * @param tasks 编排任务列表（定义了计划顺序）
     * @param results 各专家执行结果列表（可能乱序）
     * @return 合并后的共享上下文
     */
    public Map<String, Object> manageSharedContext(List<OrchestrationTask> tasks,
            List<SpecialistResult> results) {
        Map<String, Object> sharedContext = new LinkedHashMap<>();

        // 按计划顺序建立索引
        Map<String, Integer> taskOrderIndex = new LinkedHashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            taskOrderIndex.put(tasks.get(i).taskId(), i);
        }

        // 按计划顺序排列结果
        List<SpecialistResult> orderedResults = new ArrayList<>(results);
        orderedResults.sort((a, b) -> {
            int orderA = taskOrderIndex.getOrDefault(a.taskId(), Integer.MAX_VALUE);
            int orderB = taskOrderIndex.getOrDefault(b.taskId(), Integer.MAX_VALUE);
            return Integer.compare(orderA, orderB);
        });

        // 按顺序合并 outputContext 到共享上下文
        int successCount = 0;
        for (SpecialistResult result : orderedResults) {
            if (result.success()) {
                successCount++;
            }
            // 将 outputContext 扁平化到顶层
            for (Map.Entry<String, Object> entry : result.outputContext().entrySet()) {
                sharedContext.put(entry.getKey(), entry.getValue());
            }
            // 按 taskId 存储结构化结果
            Map<String, Object> taskContext = new LinkedHashMap<>();
            taskContext.put("result", result.result());
            sharedContext.put(result.taskId(), taskContext);
        }

        // 任务执行顺序
        List<String> taskOrder = tasks.stream()
                .map(OrchestrationTask::taskId)
                .collect(Collectors.toList());
        sharedContext.put(KEY_TASK_ORDER, taskOrder);

        // 协作汇总
        Map<String, Object> collaborationSummary = new LinkedHashMap<>();
        collaborationSummary.put("totalTasks", tasks.size());
        collaborationSummary.put("successCount", successCount);
        sharedContext.put(KEY_COLLABORATION_SUMMARY, collaborationSummary);

        // 质量评估
        Map<String, Object> quality = new LinkedHashMap<>();
        boolean hasFailure = successCount < tasks.size();
        String riskLevel = hasFailure ? "HIGH" : "LOW";
        quality.put("riskLevel", riskLevel);
        quality.put("successRate", tasks.isEmpty() ? 1.0D : (double) successCount / tasks.size());
        quality.put("score", tasks.isEmpty() ? 100L : (long) (successCount * 100 / tasks.size()));
        quality.put("hasFailure", hasFailure);
        quality.put("hasSkipped", false);
        sharedContext.put(KEY_QUALITY, quality);

        // 依赖图
        Map<String, List<String>> dependencyGraph = new LinkedHashMap<>();
        for (OrchestrationTask task : tasks) {
            if (!task.inputFrom().isEmpty()) {
                dependencyGraph.put(task.taskId(), new ArrayList<>(task.inputFrom()));
            }
        }
        sharedContext.put(KEY_DEPENDENCY_GRAPH, dependencyGraph);

        return sharedContext;
    }

    /**
     * 为指定任务注入其声明的依赖上下文，生成可交付给专家的 SpecialistTask。
     *
     * <p>只复制任务通过 inputFrom 声明的依赖数据，避免泄露无关的上游结果。
     * 同时注入协作汇总和依赖图供专家参考。
     *
     * @param task 编排任务
     * @param sharedContext 当前共享上下文
     * @param profile 关联的 Agent 配置
     * @return 注入依赖后的专家任务
     */
    public SpecialistTask injectSharedContext(OrchestrationTask task, Map<String, Object> sharedContext,
            com.ai.model.AgentProfile profile) {
        Map<String, Object> injectedContext = new LinkedHashMap<>();

        // 只复制声明的依赖
        Map<String, Object> dependencies = new LinkedHashMap<>();
        for (String dependencyId : task.inputFrom()) {
            Object dependencyValue = sharedContext.get(dependencyId);
            if (dependencyValue != null) {
                dependencies.put(dependencyId, dependencyValue);
            }
        }
        injectedContext.put(KEY_DEPENDENCIES, dependencies);

        // 复制协作汇总和依赖图
        if (sharedContext.containsKey(KEY_COLLABORATION_SUMMARY)) {
            injectedContext.put(KEY_COLLABORATION_SUMMARY, sharedContext.get(KEY_COLLABORATION_SUMMARY));
        }
        if (sharedContext.containsKey(KEY_DEPENDENCY_GRAPH)) {
            injectedContext.put(KEY_DEPENDENCY_GRAPH, sharedContext.get(KEY_DEPENDENCY_GRAPH));
        }

        return new SpecialistTask(task.taskId(), task.description(), task.parameters(), injectedContext, profile);
    }

    /**
     * 检测指定任务的阻塞依赖——即缺失或已失败的上游任务。
     *
     * @param task 待检测的编排任务
     * @param completedResults 已完成的任务结果映射（taskId → result）
     * @return 阻塞依赖的 taskId 列表，为空表示无阻塞
     */
    public List<String> findBlockingDependencies(OrchestrationTask task,
            Map<String, SpecialistResult> completedResults) {
        List<String> blocking = new ArrayList<>();
        for (String dependency : task.inputFrom()) {
            SpecialistResult depResult = completedResults.get(dependency);
            if (depResult == null || !depResult.success()) {
                blocking.add(dependency);
            }
        }
        return blocking;
    }
}
