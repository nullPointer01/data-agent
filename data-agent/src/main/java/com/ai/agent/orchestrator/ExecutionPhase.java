package com.ai.agent.orchestrator;

import java.util.List;

/**
 * 编排计划中的一个执行阶段。
 *
 * @param phase 阶段编号
 * @param tasks 本阶段的任务编号
 * @param parallel 本阶段是否允许并行
 * @author data-agent
 */
public record ExecutionPhase(int phase, List<String> tasks, boolean parallel) {

    public ExecutionPhase {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
