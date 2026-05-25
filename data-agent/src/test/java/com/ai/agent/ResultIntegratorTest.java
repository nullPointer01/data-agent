package com.ai.agent;

import com.ai.model.AnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.ai.agent.orchestrator.OrchestratorExecutionResult;
import com.ai.agent.orchestrator.OrchestratorDecision;

class ResultIntegratorTest {

    private final ResultIntegrator resultIntegrator = new ResultIntegrator();

    @Test
    void integrateReturnsPrimaryAnswerWithoutLeakingSharedContext() {
        OrchestratorExecutionResult executionResult = new OrchestratorExecutionResult(
                null, AnalysisResponse.ok("专家答案"), false);

        String finalAnswer = resultIntegrator.integrate("问题", null, executionResult,
                Map.of("final_answer", "上下文答案", "metric", 100,
                        "quality", Map.of("riskLevel", "LOW")));

        assertEquals("专家答案", finalAnswer);
    }

    @Test
    void integrateUsesFinalAnswerFromContextWhenPrimaryAnswerIsEmpty() {
        String finalAnswer = resultIntegrator.integrate("问题", null, null,
                Map.of("final_answer", "上下文答案"));

        assertEquals("上下文答案", finalAnswer);
    }

    @Test
    void integrateUsesSummaryWhenFinalAnswerIsMissing() {
        String finalAnswer = resultIntegrator.integrate("问题", null, null,
                Map.of("summary", "汇总答案"));

        assertEquals("汇总答案", finalAnswer);
    }

    @Test
    void integrateAppendsDiagnosticsWhenCollaborationHasFailures() {
        OrchestratorExecutionResult executionResult = new OrchestratorExecutionResult(
                null, AnalysisResponse.ok("部分完成"), false);

        String finalAnswer = resultIntegrator.integrate("问题", null, executionResult,
                Map.of("quality", Map.of("riskLevel", "HIGH"),
                        "failedTasks", List.of("t1"),
                        "skippedTasks", List.of("t3")));

        assertTrue(finalAnswer.contains("部分完成"));
        assertTrue(finalAnswer.contains("失败任务 t1"));
        assertTrue(finalAnswer.contains("跳过任务 t3"));
    }

    @Test
    void buildIntegrationSummaryUsesCollaborationQuality() {
        OrchestratorExecutionResult executionResult = new OrchestratorExecutionResult(
                null, AnalysisResponse.ok("答案"), false);

        Map<String, Object> summary = resultIntegrator.buildIntegrationSummary(executionResult,
                Map.of("quality", Map.of("score", 50L, "successRate", 0.5D,
                                "riskLevel", "HIGH", "hasFailure", true, "hasSkipped", false),
                        "collaborationSummary", Map.of("totalTasks", 2)));

        assertEquals(true, summary.get("answerAvailable"));
        assertEquals(50L, summary.get("score"));
        assertEquals("HIGH", summary.get("riskLevel"));
        assertEquals(2, summary.get("totalTasks"));
    }

    @Test
    void integrateIncludesQueryAndIntentWhenNoAnswerExists() {
        OrchestratorDecision decision = new OrchestratorDecision(
                TaskClassification.complex("复杂请求"),
                new IntentAnalysisResult(AgentIntent.COMPLEX_REASONING, AgentType.REACT, 0.5D, List.of(), "原因"),
                null,
                null,
                AgentType.REACT,
                "路由原因");

        String finalAnswer = resultIntegrator.integrate("帮我分析", decision, null, Map.of());

        assertTrue(finalAnswer.contains("帮我分析"));
        assertTrue(finalAnswer.contains("COMPLEX_REASONING"));
    }
}
