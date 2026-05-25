package com.ai.service;

import com.ai.agent.AgentReasoningProperties;
import com.ai.agent.dto.AgentReasoningHealthResponse;
import com.ai.model.AgentExecutionTrace;
import com.ai.repository.AgentExecutionTraceRepository;
import com.ai.security.SecurityContextHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentReasoningHealthServiceTest {

    private final AgentExecutionTraceRepository traceRepository = mock(AgentExecutionTraceRepository.class);
    private final SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
    private final AgentReasoningProperties reasoningProperties = new AgentReasoningProperties();
    private final AgentReasoningHealthService service = new AgentReasoningHealthService(reasoningProperties,
            traceRepository, securityContextHelper, new ObjectMapper());

    @Test
    void getCurrentTenantHealthPassesWhenRecentTracesContainReasoningEvidence() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        when(traceRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(
                        fastPathTrace(),
                        complexReasoningTrace(),
                        recoveredTrace()));

        AgentReasoningHealthResponse response = service.getCurrentTenantHealth(50);

        assertTrue(response.healthy());
        assertTrue(response.accepted());
        assertEquals(3, response.sampleSize());
        assertEquals(1L, response.simpleSampleSize());
        assertEquals(2L, response.complexSampleSize());
        assertEquals(1L, response.retryCandidateCount());
        assertEquals(1L, response.retryRecoveredCount());
        assertTrue(response.acceptanceChecks().stream().allMatch(check -> check.passed()));
    }

    @Test
    void getCurrentTenantHealthMarksSamplesPendingWhenNoTraceExists() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        when(traceRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of());

        AgentReasoningHealthResponse response = service.getCurrentTenantHealth(50);

        assertTrue(response.healthy());
        assertFalse(response.accepted());
        assertEquals(0, response.sampleSize());
        assertTrue(response.issues().stream().anyMatch(issue -> issue.contains("暂无 fast_path")));
        assertTrue(response.acceptanceChecks().stream()
                .anyMatch(check -> "TRACE_OBSERVABILITY".equals(check.key()) && "PENDING".equals(check.status())));
    }

    @Test
    void getCurrentTenantHealthFailsWhenRequiredConfigIsDisabled() {
        reasoningProperties.setPlanningEnabled(false);
        reasoningProperties.setReflectionEnabled(false);
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        when(traceRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(fastPathTrace()));

        AgentReasoningHealthResponse response = service.getCurrentTenantHealth(50);

        assertFalse(response.healthy());
        assertFalse(response.accepted());
        assertTrue(response.acceptanceChecks().stream()
                .anyMatch(check -> "PLANNING_CONFIG".equals(check.key()) && "FAILED".equals(check.status())));
        assertTrue(response.acceptanceChecks().stream()
                .anyMatch(check -> "ERROR_RECOVERY_CONFIG".equals(check.key()) && "FAILED".equals(check.status())));
    }

    private AgentExecutionTrace fastPathTrace() {
        AgentExecutionTrace trace = trace("trace-fast", true, 300L);
        trace.setComplexity("SIMPLE");
        trace.setSelectedAgent("fast-answer");
        trace.setPlanJson("""
                {"reactExecutions":[{"mode":"fast_path","iterations":0,"memoryContext":{"summaryCount":1}}]}
                """);
        return trace;
    }

    private AgentExecutionTrace complexReasoningTrace() {
        AgentExecutionTrace trace = trace("trace-complex", true, 8_000L);
        trace.setComplexity("COMPLEX");
        trace.setPlanJson("""
                {"reactExecutions":[{"mode":"reasoning","executionPlan":{"steps":[{"parallel":true}]},"parallelPrecheck":{"stepResults":[{"success":true}]},"thinkingSteps":[{"type":"parallel_precheck","content":"知识预检"}],"memoryContext":{"summaryCount":1}}]}
                """);
        return trace;
    }

    private AgentExecutionTrace recoveredTrace() {
        AgentExecutionTrace trace = trace("trace-recovered", true, 10_000L);
        trace.setComplexity("COMPLEX");
        trace.setError("SQL_ERROR retry recovered");
        trace.setTaskResultsJson("""
                [{"taskId":"t1","specialistId":"react","success":true,"executionMetadata":{"mode":"reasoning","thinkingSteps":[{"type":"reflection","content":"错误恢复后继续执行"}],"memoryContext":{"summaryCount":1}}}]
                """);
        return trace;
    }

    private AgentExecutionTrace trace(String traceId, boolean success, long durationMs) {
        AgentExecutionTrace trace = new AgentExecutionTrace();
        trace.setTraceId(traceId);
        trace.setTenantId("tenant-a");
        trace.setUserId("user-a");
        trace.setSelectedAgent("内置 ReAct");
        trace.setSelectedType("REACT");
        trace.setQuestion("测试问题");
        trace.setSuccess(success);
        trace.setDurationMs(durationMs);
        trace.setCreatedAt(LocalDateTime.of(2026, 5, 19, 10, 0));
        return trace;
    }
}
