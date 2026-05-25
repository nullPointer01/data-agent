package com.ai.agent.orchestrator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.ToolResult;
import com.ai.agent.AgentReasoningProperties;
import com.ai.agent.TaskComplexity;

class ParallelPlanExecutorTest {

    @Test
    void executeRunsSafeParallelSteps() {
        AgentToolInvoker toolInvoker = mock(AgentToolInvoker.class);
        ParallelPlanExecutor executor = newExecutor(toolInvoker);
        ExecutionPlan plan = new ExecutionPlan(TaskComplexity.COMPLEX, "test", List.of(
                new ExecutionPlanStep("retrieve-knowledge", "检索知识", "searchKnowledge", true, 1),
                new ExecutionPlanStep("inspect-data", "查看数据源", "listDataSources", true, 1)));
        when(toolInvoker.invoke(argThat(call -> call != null && "searchKnowledge".equals(call.name()))))
                .thenReturn("知识结果");
        when(toolInvoker.invoke(argThat(call -> call != null && "listDataSources".equals(call.name()))))
                .thenReturn("数据源列表");

        ParallelPlanExecutionResult result = executor.execute(plan, "退款规则");

        assertEquals(2, result.stepResults().size());
        assertTrue(result.toPromptContent().contains("知识结果"));
        assertTrue(result.toPromptContent().contains("数据源列表"));
    }

    @Test
    void executeSkipsNonParallelOrUnsafeSteps() {
        AgentToolInvoker toolInvoker = mock(AgentToolInvoker.class);
        ParallelPlanExecutor executor = newExecutor(toolInvoker);
        ExecutionPlan plan = new ExecutionPlan(TaskComplexity.COMPLEX, "test", List.of(
                new ExecutionPlanStep("execute-sql", "执行SQL", "executeSQL", true, 1),
                new ExecutionPlanStep("build-chart", "生成图表", "generateChart", false, 2)));

        ParallelPlanExecutionResult result = executor.execute(plan, "查销售");

        assertFalse(result.hasResults());
        verify(toolInvoker, never()).invoke(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void executeMarksToolFailureResultAsFailure() {
        AgentToolInvoker toolInvoker = mock(AgentToolInvoker.class);
        ParallelPlanExecutor executor = newExecutor(toolInvoker);
        ExecutionPlan plan = new ExecutionPlan(TaskComplexity.COMPLEX, "test", List.of(
                new ExecutionPlanStep("retrieve-knowledge", "检索知识", "searchKnowledge", true, 1)));
        when(toolInvoker.invoke(org.mockito.ArgumentMatchers.any()))
                .thenReturn("工具执行失败: vector down");

        ParallelPlanExecutionResult result = executor.execute(plan, "退款规则");

        assertEquals(1, result.stepResults().size());
        assertFalse(result.stepResults().get(0).success());
        assertTrue(result.toPromptContent().contains("失败"));
    }

    private ParallelPlanExecutor newExecutor(AgentToolInvoker toolInvoker) {
        return new ParallelPlanExecutor(toolInvoker, new ParallelTaskExecutor(Runnable::run), new AgentReasoningProperties());
    }
}
