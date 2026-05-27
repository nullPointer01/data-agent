package com.ai.agent;

import com.ai.mcp.McpModelService;
import com.ai.model.AnalysisRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ai.agent.orchestrator.OrchestratorProperties;

class IntentAnalyzerTest {

    private final IntentAnalyzer analyzer = new IntentAnalyzer(null, new ObjectMapper(), new OrchestratorProperties());

    @Test
    void analyzeDetectsDataAnalysisIntentFromMetricSignals() {
        IntentAnalysisResult result = analyzer.analyze(
                request("分析销售额同比趋势并画图"),
                null,
                TaskClassification.toolAssisted("data"));

        assertEquals(AgentIntent.DATA_ANALYSIS, result.intent());
        assertEquals(AgentType.DATA, result.preferredType());
        assertTrue(result.matchedSignals().contains("销售"));
    }

    @Test
    void analyzeDetectsKnowledgeRetrievalIntent() {
        IntentAnalysisResult result = analyzer.analyze(
                request("检索知识库里的报销制度并给出处来源"),
                null,
                TaskClassification.complex("knowledge"));

        assertEquals(AgentIntent.KNOWLEDGE_RETRIEVAL, result.intent());
        assertEquals(AgentType.KNOWLEDGE, result.preferredType());
        assertTrue(result.matchedSignals().contains("知识库"));
    }

    @Test
    void analyzeDetectsReportGenerationIntent() {
        IntentAnalysisResult result = analyzer.analyze(
                request("生成一份客户拜访总结报告"),
                null,
                TaskClassification.complex("report"));

        assertEquals(AgentIntent.REPORT_GENERATION, result.intent());
        assertEquals(AgentType.REPORT, result.preferredType());
    }

    @Test
    void analyzePrefersUploadedFileAsDataIntent() {
        AnalysisRequest request = request("帮我看看这个内容");
        request.setFileId("file-1");

        IntentAnalysisResult result = analyzer.analyze(request, "name,value", TaskClassification.toolAssisted("file"));

        assertEquals(AgentIntent.DATA_ANALYSIS, result.intent());
        assertEquals(AgentType.DATA, result.preferredType());
        assertTrue(result.matchedSignals().contains("uploaded-file"));
    }

    @Test
    void analyzeDoesNotCallModelWhenLlmIntentIsDisabled() {
        McpModelService modelService = mock(McpModelService.class);
        IntentAnalyzer llmDisabledAnalyzer = new IntentAnalyzer(modelService, new ObjectMapper(),
                new OrchestratorProperties());

        IntentAnalysisResult result = llmDisabledAnalyzer.analyze(
                request("帮我判断下一步怎么做"),
                null,
                TaskClassification.complex("complex"));

        assertEquals(AgentIntent.COMPLEX_REASONING, result.intent());
        verify(modelService, never()).callModel(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void analyzeUsesLlmForLowConfidenceIntentWhenEnabled() {
        McpModelService modelService = mock(McpModelService.class);
        OrchestratorProperties properties = new OrchestratorProperties();
        properties.setLlmIntentEnabled(true);
        IntentAnalyzer llmAnalyzer = new IntentAnalyzer(modelService, new ObjectMapper(), properties);
        when(modelService.callModel(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("""
                        {"intent":"REPORT_GENERATION","preferredType":"SKILL","confidence":0.92,"matchedSignals":["复盘","报告"],"reason":"用户需要生成复盘报告"}
                        """);

        IntentAnalysisResult result = llmAnalyzer.analyze(
                request("帮我整理这件事情的复盘材料"),
                null,
                TaskClassification.complex("complex"));

        assertEquals(AgentIntent.REPORT_GENERATION, result.intent());
        assertEquals(AgentType.SKILL, result.preferredType());
        assertTrue(result.reason().contains("LLM增强"));
    }

    private AnalysisRequest request(String question) {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion(question);
        return request;
    }
}
