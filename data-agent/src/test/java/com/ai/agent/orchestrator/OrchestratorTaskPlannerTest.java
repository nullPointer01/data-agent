package com.ai.agent.orchestrator;

import com.ai.mcp.McpModelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.ai.agent.AgentIntent;
import com.ai.agent.AgentType;
import com.ai.agent.IntentAnalysisResult;

class OrchestratorTaskPlannerTest {

    private final OrchestratorTaskPlanner planner = new OrchestratorTaskPlanner(null, new ObjectMapper(), new OrchestratorProperties());

    @Test
    void planCreatesDataSpecialistTaskForDataIntent() {
        IntentAnalysisResult intentAnalysis = new IntentAnalysisResult(
                AgentIntent.DATA_ANALYSIS, AgentType.DATA, 0.9D, java.util.List.of("数据"), "命中数据");

        OrchestrationPlan plan = planner.plan("分析销售", intentAnalysis);

        assertTrue(plan.hasTasks());
        assertEquals("分析销售", plan.originalQuery());
        assertEquals(AgentType.DATA.name(), plan.tasks().get(0).specialistName());
        assertTrue(plan.sharedContextKeys().contains("analysis_result"));
    }

    @Test
    void planSimpleCreatesSingleTaskPlan() {
        IntentAnalysisResult intentAnalysis = new IntentAnalysisResult(
                AgentIntent.GENERAL_CHAT, AgentType.CHAT, 0.75D, java.util.List.of("fast-path"), "简单问题");

        OrchestrationPlan plan = planner.planSimple(intentAnalysis);

        assertTrue(plan.hasTasks());
        assertFalse(plan.executionPhases().isEmpty());
        assertEquals(AgentType.CHAT.name(), plan.tasks().get(0).specialistName());
    }

    @Test
    void planCreatesCollaborativeReportPlanForReportIntent() {
        IntentAnalysisResult intentAnalysis = new IntentAnalysisResult(
                AgentIntent.REPORT_GENERATION, AgentType.REPORT, 0.9D, java.util.List.of("报告"), "命中报告");

        OrchestrationPlan plan = planner.plan("生成销售报告", intentAnalysis);

        assertEquals(4, plan.tasks().size());
        assertEquals(3, plan.executionPhases().size());
        assertTrue(plan.executionPhases().get(0).parallel());
        assertEquals(java.util.List.of("t1", "t2"), plan.executionPhases().get(0).tasks());
        assertEquals(AgentType.REPORT.name(), plan.tasks().get(3).specialistName());
    }

    @Test
    void planCreatesChartGenerationPlanForChartIntent() {
        IntentAnalysisResult intentAnalysis = new IntentAnalysisResult(
                AgentIntent.DATA_ANALYSIS, AgentType.CHART, 0.9D, java.util.List.of("图表"), "命中图表");

        OrchestrationPlan plan = planner.plan("生成销售图表", intentAnalysis);

        assertEquals(2, plan.tasks().size());
        assertEquals(AgentType.CHART.name(), plan.tasks().get(1).specialistName());
    }

    @Test
    void planCreatesTwoPhaseDataAnalysisPlan() {
        IntentAnalysisResult intentAnalysis = new IntentAnalysisResult(
                AgentIntent.DATA_ANALYSIS, AgentType.DATA, 0.9D, java.util.List.of("销售"), "命中数据");

        OrchestrationPlan plan = planner.plan("分析销售趋势", intentAnalysis);

        assertEquals(2, plan.tasks().size());
        assertEquals(2, plan.executionPhases().size());
        assertEquals("t0", plan.tasks().get(0).taskId());
        assertEquals("t1", plan.tasks().get(1).taskId());
        assertTrue(plan.executionPhases().get(0).parallel());
    }

    @Test
    void planCreatesTwoPhaseKnowledgeRetrievalPlan() {
        IntentAnalysisResult intentAnalysis = new IntentAnalysisResult(
                AgentIntent.KNOWLEDGE_RETRIEVAL, AgentType.KNOWLEDGE, 0.9D, java.util.List.of("知识库"), "命中检索");

        OrchestrationPlan plan = planner.plan("检索制度", intentAnalysis);

        assertEquals(2, plan.tasks().size());
        assertEquals(2, plan.executionPhases().size());
        assertEquals("t2", plan.tasks().get(0).taskId());
        assertEquals(AgentType.KNOWLEDGE.name(), plan.tasks().get(0).specialistName());
        assertEquals("t3", plan.tasks().get(1).taskId());
        assertEquals(AgentType.REACT.name(), plan.tasks().get(1).specialistName());
    }

    @Test
    void planUsesLlmPlanningWhenEnabledForCollaborativeIntent() {
        McpModelService modelService = mock(McpModelService.class);
        OrchestratorProperties properties = new OrchestratorProperties();
        properties.setLlmPlanningEnabled(true);
        OrchestratorTaskPlanner llmPlanner = new OrchestratorTaskPlanner(modelService, new ObjectMapper(), properties);
        when(modelService.callModel(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("""
                        {
                          "sharedContextKeys":["sales","policy"],
                          "tasks":[
                            {"taskId":"t1","description":"收集销售数据","specialistName":"DATA","inputFrom":[],"outputTo":["t3"],"canParallelWith":["t2"],"expectedOutput":"销售事实"},
                            {"taskId":"t2","description":"检索政策资料","specialistName":"REACT","inputFrom":[],"outputTo":["t3"],"canParallelWith":["t1"],"expectedOutput":"政策依据"},
                            {"taskId":"t3","description":"生成分析报告","specialistName":"SKILL","inputFrom":["t1","t2"],"outputTo":[],"canParallelWith":[],"expectedOutput":"报告"}
                          ],
                          "executionPhases":[
                            {"phase":1,"tasks":["t1","t2"],"parallel":true},
                            {"phase":2,"tasks":["t3"],"parallel":false}
                          ]
                        }
                        """);
        IntentAnalysisResult intentAnalysis = new IntentAnalysisResult(
                AgentIntent.REPORT_GENERATION, AgentType.SKILL, 0.9D, java.util.List.of("报告"), "命中报告");

        OrchestrationPlan plan = llmPlanner.plan("生成销售复盘报告", intentAnalysis);

        assertEquals(3, plan.tasks().size());
        assertEquals(2, plan.executionPhases().size());
        assertTrue(plan.executionPhases().get(0).parallel());
        assertEquals(AgentType.SKILL.name(), plan.tasks().get(2).specialistName());
        assertTrue(plan.sharedContextKeys().contains("sales"));
    }
}
