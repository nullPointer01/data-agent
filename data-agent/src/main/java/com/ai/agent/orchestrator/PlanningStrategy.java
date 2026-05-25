package com.ai.agent.orchestrator;

import com.ai.model.AnalysisRequest;
import com.ai.agent.TaskClassification;
import com.ai.agent.react.ReActRequestContext;

/**
 * 执行计划构建策略接口。
 *
 * <p>供 {@link TaskPlanner} 或其替换实现使用，从请求上下文和分类结果
 * 构建 {@link ExecutionPlan}。</p>
 *
 * @author data-agent
 */
public interface PlanningStrategy {

    /**
     * 根据请求上下文和分类结果构建执行计划。
     *
     * @param context ReAct 请求上下文
     * @param classification 任务分类结果
     * @return 执行计划
     */
    ExecutionPlan buildPlan(ReActRequestContext context, TaskClassification classification);
}
