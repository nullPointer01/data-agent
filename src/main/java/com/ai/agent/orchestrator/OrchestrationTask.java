package com.ai.agent.orchestrator;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 供协作管理器使用的编排任务。
 *
 * @param taskId 稳定任务编号
 * @param description 任务描述
 * @param specialistName 任务绑定的专家名称
 * @param parameters 任务参数
 * @param inputFrom 依赖的任务编号
 * @param outputTo 下游任务编号
 * @param canParallelWith 可并行的同级任务编号
 * @param expectedOutput 预期输出说明
 * @author data-agent
 */
public record OrchestrationTask(String taskId,
        String description,
        String specialistName,
        Map<String, Object> parameters,
        List<String> inputFrom,
        List<String> outputTo,
        List<String> canParallelWith,
        String expectedOutput) {

    public OrchestrationTask {
        taskId = taskId == null ? "task" : taskId;
        description = description == null ? "" : description;
        specialistName = specialistName == null ? "" : specialistName;
        parameters = parameters == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(parameters));
        inputFrom = inputFrom == null ? List.of() : List.copyOf(inputFrom);
        outputTo = outputTo == null ? List.of() : List.copyOf(outputTo);
        canParallelWith = canParallelWith == null ? List.of() : List.copyOf(canParallelWith);
        expectedOutput = expectedOutput == null ? "" : expectedOutput;
    }
}
