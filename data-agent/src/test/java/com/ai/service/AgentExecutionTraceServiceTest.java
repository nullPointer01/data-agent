package com.ai.service;

import com.ai.agent.AgentIntent;
import com.ai.agent.AgentType;
import com.ai.agent.IntentAnalysisResult;
import com.ai.agent.orchestrator.OrchestratorDecision;
import com.ai.agent.orchestrator.OrchestratorExecutionResult;
import com.ai.agent.orchestrator.OrchestratorResult;
import com.ai.agent.orchestrator.OrchestrationPlan;
import com.ai.agent.orchestrator.OrchestrationTask;
import com.ai.agent.specialist.SpecialistResult;
import com.ai.agent.TaskClassification;
import com.ai.agent.dto.AgentExecutionTraceListResponse;
import com.ai.logging.StructuredLogger;
import com.ai.model.AgentExecutionTrace;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.model.ConversationSession;
import com.ai.repository.AgentExecutionTraceRepository;
import com.ai.security.SecurityContextHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentExecutionTraceServiceTest {

    private final AgentExecutionTraceRepository repository = mock(AgentExecutionTraceRepository.class);
    private final SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
    private final StructuredLogger structuredLogger = mock(StructuredLogger.class);
    private final AgentExecutionTraceService service = new AgentExecutionTraceService(repository,
            securityContextHelper, new ObjectMapper(), new SimpleMeterRegistry(), structuredLogger);

    @Test
    void recordPersistsTraceWithTenantAndDecisionMetadata() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        when(securityContextHelper.getCurrentUserId()).thenReturn("user-a");
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("分析销售趋势");
        ConversationSession session = new ConversationSession("session-1");
        OrchestratorResult result = orchestratorResult(request);

        String traceId = service.record(request, session, result);

        var captor = forClass(AgentExecutionTrace.class);
        verify(repository).save(captor.capture());
        AgentExecutionTrace trace = captor.getValue();
        assertEquals(trace.getTraceId(), traceId);
        assertEquals("tenant-a", trace.getTenantId());
        assertEquals("user-a", trace.getUserId());
        assertEquals("session-1", trace.getSessionId());
        assertEquals("内置 ReAct", trace.getSelectedAgent());
        assertEquals("REACT", trace.getSelectedType());
        assertEquals("DATA_ANALYSIS", trace.getIntent());
        assertEquals("TOOL_ASSISTED", trace.getComplexity());
        assertEquals(1, trace.getTaskCount());
        assertJsonContains(trace.getPlanJson(), "\"reactExecutions\"");
        assertJsonContains(trace.getPlanJson(), "\"executionPlan\"");
        assertJsonContains(trace.getTaskResultsJson(), "\"executionMetadata\"");
        assertJsonContains(trace.getSharedContextJson(), "\"reactExecutions\"");
        verify(structuredLogger).logEvent(org.mockito.ArgumentMatchers.eq(StructuredLogger.TYPE_AGENT_TRACE),
                org.mockito.ArgumentMatchers.any(Map.class));
    }

    @Test
    void listCurrentTenantReturnsTenantTraces() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        AgentExecutionTrace trace = new AgentExecutionTrace();
        trace.setTraceId("trace-1");
        trace.setTenantId("tenant-a");
        when(repository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(trace));

        AgentExecutionTraceListResponse response = service.listCurrentTenant(10, null);

        assertEquals(true, response.success());
        assertEquals("trace-1", response.traces().get(0).traceId());
    }

    @Test
    void getCurrentTenantTraceRejectsMissingTrace() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        when(repository.findByTraceIdAndTenantId("trace-1", "tenant-a")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.getCurrentTenantTrace("trace-1"));
    }

    private OrchestratorResult orchestratorResult(AnalysisRequest request) {
        OrchestrationTask task = new OrchestrationTask("t1", "分析数据", "REACT",
                Map.of(), List.of(), List.of(), List.of(), "分析结果");
        OrchestrationPlan plan = new OrchestrationPlan("plan-1", request.getQuestion(), List.of(task),
                List.of(), List.of(), 1L);
        IntentAnalysisResult intent = new IntentAnalysisResult(AgentIntent.DATA_ANALYSIS, AgentType.DATA, 0.9D,
                List.of("销售"), "命中数据分析意图");
        OrchestratorDecision decision = new OrchestratorDecision(TaskClassification.toolAssisted("需要工具"),
                intent, plan, null, AgentType.REACT, "使用内置 ReAct");
        SpecialistResult taskResult = new SpecialistResult("t1", "内置 ReAct", true,
                "answer", "", 12L, Map.of(
                        "final_answer", "answer",
                        "execution_metadata", Map.of(
                                "mode", "reasoning",
                                "iterations", 2,
                                "executionPlan", Map.of("steps", List.of("retrieve-knowledge")))),
                List.of());
        AnalysisResponse response = AnalysisResponse.ok("answer");
        OrchestratorExecutionResult executionResult = new OrchestratorExecutionResult(List.of(taskResult), response,
                true);
        executionResult.setDecision(decision);
        return new OrchestratorResult(true, "answer", AgentType.REACT, "内置 ReAct", executionResult, plan,
                Map.of("final_answer", "answer"), intent);
    }

    private void assertJsonContains(String json, String expected) {
        org.junit.jupiter.api.Assertions.assertTrue(json.contains(expected),
                () -> "期望 JSON 包含 " + expected + "，实际为: " + json);
    }
}
