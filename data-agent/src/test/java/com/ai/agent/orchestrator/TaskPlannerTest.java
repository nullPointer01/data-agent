package com.ai.agent.orchestrator;

import com.ai.model.AnalysisRequest;
import com.ai.rag.dto.RagContextResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.ai.agent.TaskClassification;
import com.ai.agent.TaskComplexity;
import com.ai.agent.react.ReActRequestContext;

class TaskPlannerTest {

    private final TaskPlanner taskPlanner = new TaskPlanner();

    @Test
    void planCreatesDirectAnswerStepForSimpleRequest() {
        AnalysisRequest request = request("你好");
        TaskClassification classification = TaskClassification.simple("问候");

        ExecutionPlan plan = taskPlanner.plan(request, new ReActRequestContext("你好", RagContextResponse.empty()),
                classification);

        assertEquals(TaskComplexity.SIMPLE, plan.complexity());
        assertEquals(1, plan.steps().size());
        assertTrue(plan.toThinkingContent().contains("直接回答"));
    }

    @Test
    void planCreatesDataAnalysisStepsForBusinessQuestion() {
        AnalysisRequest request = request("分析 Q3 销售趋势并生成图表");
        TaskClassification classification = TaskClassification.toolAssisted("业务分析");

        ExecutionPlan plan = taskPlanner.plan(request, new ReActRequestContext(request.getQuestion(),
                RagContextResponse.empty()), classification);

        assertTrue(plan.steps().stream().anyMatch(step -> "inspect-data".equals(step.id())));
        assertTrue(plan.steps().stream().anyMatch(step -> "build-chart".equals(step.id())));
        assertTrue(plan.steps().stream().anyMatch(step -> "inspect-data".equals(step.id()) && step.phase() == 1));
        assertTrue(plan.steps().stream().anyMatch(step -> "analyze-metrics".equals(step.id()) && step.phase() == 2));
        assertTrue(plan.steps().stream().anyMatch(step -> "build-chart".equals(step.id()) && step.phase() == 3));
        assertTrue(plan.toPromptContent().contains("请按照以下计划执行"));
        assertTrue(plan.toPromptContent().contains("阶段 1"));
        assertTrue(plan.toPromptContent().contains("阶段 3"));
    }

    @Test
    void planUsesKnowledgeStepWhenRagContextExists() {
        AnalysisRequest request = request("这个制度怎么解释");
        TaskClassification classification = TaskClassification.complex("默认推理");
        ReActRequestContext context = new ReActRequestContext(request.getQuestion(),
                new RagContextResponse("制度资料", 1));

        ExecutionPlan plan = taskPlanner.plan(request, context, classification);

        assertTrue(plan.steps().stream().anyMatch(step -> "retrieve-knowledge".equals(step.id())));
    }

    private AnalysisRequest request(String question) {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion(question);
        return request;
    }
}
