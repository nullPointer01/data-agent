package com.ai.agent.specialist;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 供协作层使用的专家执行结果。
 *
 * @param taskId 任务编号
 * @param specialistId 专家标识
 * @param success 是否执行成功
 * @param result 专家输出
 * @param error 失败时的错误信息
 * @param executionTimeMs 执行耗时
 * @param outputContext 供后续任务共享的结构化数据
 * @param nextSuggestedTasks 专家建议的后续任务
 * @author data-agent
 */
public record SpecialistResult(String taskId,
        String specialistId,
        boolean success,
        String result,
        String error,
        long executionTimeMs,
        Map<String, Object> outputContext,
        List<String> nextSuggestedTasks) {

    public SpecialistResult {
        taskId = taskId == null ? "task" : taskId;
        specialistId = specialistId == null ? "" : specialistId;
        result = result == null ? "" : result;
        error = error == null ? "" : error;
        outputContext = outputContext == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(outputContext));
        nextSuggestedTasks = nextSuggestedTasks == null ? List.of() : List.copyOf(nextSuggestedTasks);
    }
}
