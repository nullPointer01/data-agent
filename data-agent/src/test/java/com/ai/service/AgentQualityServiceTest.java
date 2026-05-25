package com.ai.service;

import com.ai.agent.dto.AgentQualityDashboardResponse;
import com.ai.model.AgentExecutionTrace;
import com.ai.model.AgentFeedback;
import com.ai.repository.AgentExecutionTraceRepository;
import com.ai.repository.AgentFeedbackRepository;
import com.ai.security.SecurityContextHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentQualityServiceTest {

    private final AgentExecutionTraceRepository traceRepository = mock(AgentExecutionTraceRepository.class);
    private final AgentFeedbackRepository feedbackRepository = mock(AgentFeedbackRepository.class);
    private final SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
    private final AgentQualityService service = new AgentQualityService(traceRepository, feedbackRepository,
            securityContextHelper, new ObjectMapper());

    @Test
    void getCurrentTenantDashboardCalculatesQualityMetrics() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        when(traceRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(
                        trace("trace-1", "数据分析", "DATA", true, false, 1_000L),
                        trace("trace-2", "数据分析", "DATA", false, true, 19_000L,
                                "工具执行失败: SQL syntax error")));
        when(feedbackRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(
                        feedback("trace-1", "UP"),
                        feedback("trace-2", "DOWN")));
        when(feedbackRepository.countByTenantId("tenant-a")).thenReturn(2L);
        when(feedbackRepository.countByTenantIdAndRating("tenant-a", "UP")).thenReturn(1L);
        when(feedbackRepository.countByTenantIdAndRating("tenant-a", "DOWN")).thenReturn(1L);

        AgentQualityDashboardResponse response = service.getCurrentTenantDashboard(20);

        assertTrue(response.success());
        assertEquals(2, response.sampleSize());
        assertEquals(50D, response.successRate() * 100D);
        assertEquals(50D, response.positiveRate() * 100D);
        assertEquals(10_000L, response.averageDurationMs());
        assertEquals(1, response.agentStats().size());
        assertEquals("DATA:数据分析", response.agentStats().get(0).agentKey());
        assertEquals(2L, response.agentStats().get(0).traceCount());
        assertEquals(1L, response.agentStats().get(0).negativeFeedbackCount());
        assertEquals(1, response.trendPoints().size());
        assertEquals("TOOL_DATASOURCE", response.attributions().get(0).causeCode());
        assertTrue(response.risks().stream().anyMatch((risk) -> "LOW_SUCCESS_RATE".equals(risk.riskCode())));
        assertTrue(response.recommendations().stream().anyMatch((item) -> item.contains("失败轨迹")));
        verify(traceRepository).findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class));
    }

    @Test
    void getCurrentTenantDashboardHandlesEmptyTraceSamples() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        when(traceRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(feedbackRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of());

        AgentQualityDashboardResponse response = service.getCurrentTenantDashboard(20);

        assertTrue(response.success());
        assertEquals(0, response.sampleSize());
        assertEquals(0, response.qualityScore());
        assertEquals("RISK", response.qualityLevel());
        assertEquals("NO_TRACE", response.risks().get(0).riskCode());
        assertTrue(response.trendPoints().isEmpty());
    }

    @Test
    void getCurrentTenantDashboardClassifiesFailureAttributions() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        when(traceRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(
                        trace("trace-1", "默认 Agent", "REACT", false, false, 900L,
                                "Model call failed after retries"),
                        trace("trace-2", "默认 Agent", "REACT", false, false, 800L,
                                "Milvus vector retrieval failed"),
                        trace("trace-3", "默认 Agent", "REACT", true, true, 1_000L,
                                "jwt permission denied")));
        when(feedbackRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of());

        AgentQualityDashboardResponse response = service.getCurrentTenantDashboard(20);

        assertEquals(3, response.attributions().size());
        assertTrue(response.attributions().stream()
                .anyMatch((item) -> "MODEL_CALL".equals(item.causeCode())));
        assertTrue(response.attributions().stream()
                .anyMatch((item) -> "RAG_KNOWLEDGE".equals(item.causeCode())));
        assertTrue(response.attributions().stream()
                .anyMatch((item) -> "SECURITY_QUOTA".equals(item.causeCode())));
        assertTrue(response.recommendations().stream().anyMatch((item) -> item.contains("备用模型")));
    }

    @Test
    void getCurrentTenantDashboardDetectsReactIterationLimitFromStructuredMetadata() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        AgentExecutionTrace trace = trace("trace-1", "默认 Agent", "REACT", false, false, 12_000L,
                "达到最大迭代次数，建议缩小问题范围。");
        trace.setPlanJson("""
                {"reactExecutions":[{"mode":"reasoning","iterations":8,"thinkingSteps":[{"type":"max_iterations","content":"达到最大迭代次数"}]}]}
                """);
        when(traceRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(trace));
        when(feedbackRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of());

        AgentQualityDashboardResponse response = service.getCurrentTenantDashboard(20);

        assertTrue(response.attributions().stream()
                .anyMatch((item) -> "REACT_ITERATION_LIMIT".equals(item.causeCode())));
        assertTrue(response.risks().stream()
                .anyMatch((item) -> "REACT_ITERATION_LIMIT".equals(item.riskCode())));
    }

    @Test
    void getCurrentTenantDashboardDetectsDependencyBlockedTraces() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        AgentExecutionTrace trace = trace("trace-1", "默认 Agent", "REACT", false, false, 1_000L,
                "上游依赖失败或缺失，跳过任务: t3");
        trace.setTaskResultsJson("""
                [{"taskId":"t3","specialistId":"编排器","success":false,"result":"","error":"上游依赖失败或缺失，跳过任务: t1","executionTimeMs":0,"outputContext":{"taskId":"t3","status":"SKIPPED","skipped":true,"skipReason":"上游依赖失败或缺失，跳过任务: t1","blockingDependencies":["t1"]},"nextSuggestedTasks":[]}]
                """);
        when(traceRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(trace));
        when(feedbackRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of());

        AgentQualityDashboardResponse response = service.getCurrentTenantDashboard(20);

        assertTrue(response.attributions().stream()
                .anyMatch((item) -> "DEPENDENCY_BLOCKED".equals(item.causeCode())));
        assertTrue(response.risks().stream()
                .anyMatch((item) -> "DEPENDENCY_BLOCKED".equals(item.riskCode())));
    }

    @Test
    void getCurrentTenantDashboardBuildsTrendPointsByDate() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        AgentExecutionTrace firstDayTrace = trace("trace-1", "默认 Agent", "REACT", true, false, 1_000L);
        firstDayTrace.setCreatedAt(LocalDateTime.of(2026, 5, 10, 10, 0));
        AgentExecutionTrace secondDayTrace = trace("trace-2", "默认 Agent", "REACT", false, true, 3_000L,
                "工具执行失败");
        secondDayTrace.setCreatedAt(LocalDateTime.of(2026, 5, 11, 10, 0));
        when(traceRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(firstDayTrace, secondDayTrace));
        AgentFeedback firstDayFeedback = feedback("trace-1", "UP");
        firstDayFeedback.setCreatedAt(LocalDateTime.of(2026, 5, 10, 12, 0));
        AgentFeedback secondDayFeedback = feedback("trace-2", "DOWN");
        secondDayFeedback.setCreatedAt(LocalDateTime.of(2026, 5, 11, 12, 0));
        when(feedbackRepository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(firstDayFeedback, secondDayFeedback));
        when(feedbackRepository.countByTenantId("tenant-a")).thenReturn(2L);
        when(feedbackRepository.countByTenantIdAndRating("tenant-a", "UP")).thenReturn(1L);
        when(feedbackRepository.countByTenantIdAndRating("tenant-a", "DOWN")).thenReturn(1L);

        AgentQualityDashboardResponse response = service.getCurrentTenantDashboard(20);

        assertEquals(2, response.trendPoints().size());
        assertEquals("2026-05-10", response.trendPoints().get(0).date());
        assertEquals(100, response.trendPoints().get(0).qualityScore());
        assertEquals("2026-05-11", response.trendPoints().get(1).date());
        assertTrue(response.trendPoints().get(1).qualityScore() < response.trendPoints().get(0).qualityScore());
    }

    private AgentExecutionTrace trace(String traceId, String selectedAgent, String selectedType,
            boolean success, boolean fallbackUsed, long durationMs) {
        return trace(traceId, selectedAgent, selectedType, success, fallbackUsed, durationMs, null);
    }

    private AgentExecutionTrace trace(String traceId, String selectedAgent, String selectedType,
            boolean success, boolean fallbackUsed, long durationMs, String error) {
        AgentExecutionTrace trace = new AgentExecutionTrace();
        trace.setTraceId(traceId);
        trace.setTenantId("tenant-a");
        trace.setSelectedAgent(selectedAgent);
        trace.setSelectedType(selectedType);
        trace.setSuccess(success);
        trace.setFallbackUsed(fallbackUsed);
        trace.setDurationMs(durationMs);
        trace.setQuestion("测试问题");
        trace.setError(error);
        trace.setCreatedAt(LocalDateTime.of(2026, 5, 10, 10, 0));
        return trace;
    }

    private AgentFeedback feedback(String traceId, String rating) {
        AgentFeedback feedback = new AgentFeedback();
        feedback.setFeedbackId("feedback-" + traceId);
        feedback.setTenantId("tenant-a");
        feedback.setTraceId(traceId);
        feedback.setRating(rating);
        feedback.setCreatedAt(LocalDateTime.of(2026, 5, 10, 12, 0));
        return feedback;
    }
}
