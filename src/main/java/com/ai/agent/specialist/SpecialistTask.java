package com.ai.agent.specialist;

import com.ai.model.AgentProfile;

import java.util.Collections;
import java.util.Map;

/**
 * 注入共享上下文后传给专家的任务。
 *
 * @param taskId 稳定任务编号
 * @param description 任务描述
 * @param parameters 任务参数
 * @param sharedContext 编排器注入的依赖上下文
 * @param profile 关联的专家配置，可为空
 * @author data-agent
 */
public record SpecialistTask(String taskId,
        String description,
        Map<String, Object> parameters,
        Map<String, Object> sharedContext,
        AgentProfile profile) {

    public SpecialistTask {
        taskId = taskId == null ? "task" : taskId;
        description = description == null ? "" : description;
        parameters = parameters == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(parameters));
        sharedContext = sharedContext == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(sharedContext));
    }
}
