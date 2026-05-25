package com.ai.service;

import com.ai.agent.dto.AgentFeedbackListResponse;
import com.ai.agent.dto.AgentFeedbackDashboardResponse;
import com.ai.agent.dto.AgentFeedbackMutationResponse;
import com.ai.agent.dto.AgentFeedbackRequest;
import com.ai.agent.dto.AgentFeedbackSummaryResponse;
import com.ai.logging.StructuredLogger;
import com.ai.model.AgentFeedback;
import com.ai.repository.AgentFeedbackRepository;
import com.ai.security.SecurityContextHelper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentFeedbackServiceTest {

    private final AgentFeedbackRepository repository = mock(AgentFeedbackRepository.class);
    private final SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AgentFeedbackInsightAnalyzer insightAnalyzer = mock(AgentFeedbackInsightAnalyzer.class);
    private final AgentFeedbackInsightProperties insightProperties = new AgentFeedbackInsightProperties(
            null, null, null, null, 25, null);
    private final StructuredLogger structuredLogger = mock(StructuredLogger.class);
    private final AgentFeedbackService service = new AgentFeedbackService(repository, securityContextHelper,
            meterRegistry, insightAnalyzer, insightProperties, structuredLogger);

    @Test
    void saveCurrentUserFeedbackPersistsTenantUserAndNormalizedRating() {
        mockCurrentUser();
        AgentFeedbackRequest request = new AgentFeedbackRequest("session-1", "trace-1",
                "up", "问题", "回答", "很好");
        when(repository.findByTraceIdAndUserIdAndTenantId("trace-1", "user-a", "tenant-a"))
                .thenReturn(Optional.empty());

        AgentFeedbackMutationResponse response = service.saveCurrentUserFeedback(request);

        var captor = forClass(AgentFeedback.class);
        verify(repository).save(captor.capture());
        AgentFeedback feedback = captor.getValue();
        assertTrue(response.success());
        assertEquals(feedback.getFeedbackId(), response.feedbackId());
        assertEquals("tenant-a", feedback.getTenantId());
        assertEquals("user-a", feedback.getUserId());
        assertEquals("session-1", feedback.getSessionId());
        assertEquals("trace-1", feedback.getTraceId());
        assertEquals("UP", feedback.getRating());
        assertEquals("问题", feedback.getQuestion());
        assertEquals("回答", feedback.getAnswer());
        assertEquals("很好", feedback.getComment());
        assertEquals(1D, meterRegistry.counter("data_agent_feedback_total", "rating", "UP").count());
        verify(structuredLogger).logEvent(org.mockito.ArgumentMatchers.eq(StructuredLogger.TYPE_AGENT_FEEDBACK),
                org.mockito.ArgumentMatchers.any(Map.class));
    }

    @Test
    void saveCurrentUserFeedbackUpdatesExistingFeedbackForSameTrace() {
        mockCurrentUser();
        AgentFeedback existing = new AgentFeedback();
        existing.setFeedbackId("feedback-1");
        when(repository.findByTraceIdAndUserIdAndTenantId("trace-1", "user-a", "tenant-a"))
                .thenReturn(Optional.of(existing));
        AgentFeedbackRequest request = new AgentFeedbackRequest("session-1", "trace-1",
                "down", "问题", "回答", "不准确");

        AgentFeedbackMutationResponse response = service.saveCurrentUserFeedback(request);

        verify(repository).save(existing);
        assertEquals("feedback-1", response.feedbackId());
        assertEquals("DOWN", existing.getRating());
        assertEquals("不准确", existing.getComment());
    }

    @Test
    void saveCurrentUserFeedbackRejectsInvalidRating() {
        AgentFeedbackRequest request = new AgentFeedbackRequest("session-1", "trace-1",
                "middle", "问题", "回答", null);

        assertThrows(IllegalArgumentException.class, () -> service.saveCurrentUserFeedback(request));
    }

    @Test
    void saveCurrentUserFeedbackRequiresSessionOrTrace() {
        AgentFeedbackRequest request = new AgentFeedbackRequest(null, null, "UP", "问题", "回答", null);

        assertThrows(IllegalArgumentException.class, () -> service.saveCurrentUserFeedback(request));
    }

    @Test
    void listCurrentTenantFeedbacksReturnsTenantFeedbacks() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        AgentFeedback feedback = new AgentFeedback();
        feedback.setFeedbackId("feedback-1");
        feedback.setTenantId("tenant-a");
        feedback.setUserId("user-a");
        feedback.setRating("UP");
        when(repository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(feedback));

        AgentFeedbackListResponse response = service.listCurrentTenantFeedbacks(10);

        assertTrue(response.success());
        assertEquals("feedback-1", response.feedbacks().get(0).feedbackId());
        verify(repository).findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class));
    }

    @Test
    void listCurrentTenantFeedbacksCanFilterByTraceId() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        AgentFeedback feedback = new AgentFeedback();
        feedback.setFeedbackId("feedback-1");
        feedback.setTenantId("tenant-a");
        feedback.setTraceId("trace-1");
        feedback.setRating("DOWN");
        when(repository.findByTenantIdAndTraceIdOrderByCreatedAtDesc(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(feedback));

        AgentFeedbackListResponse response = service.listCurrentTenantFeedbacks(10, "trace-1", null);

        assertTrue(response.success());
        assertEquals("trace-1", response.feedbacks().get(0).traceId());
        verify(repository).findByTenantIdAndTraceIdOrderByCreatedAtDesc(any(), any(), any(Pageable.class));
    }

    @Test
    void listCurrentTenantFeedbacksCanFilterByRating() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        AgentFeedback feedback = feedback("feedback-1", "DOWN", "不准确", "问题", "回答");
        when(repository.findByTenantIdAndRatingOrderByCreatedAtDesc(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(feedback));

        AgentFeedbackListResponse response = service.listCurrentTenantFeedbacks(10, null, "down");

        assertTrue(response.success());
        assertEquals("DOWN", response.feedbacks().get(0).rating());
        verify(repository).findByTenantIdAndRatingOrderByCreatedAtDesc(any(), any(), any(Pageable.class));
    }

    @Test
    void listCurrentTenantFeedbacksCanFilterByTraceIdAndRating() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        AgentFeedback feedback = feedback("feedback-1", "DOWN", "不准确", "问题", "回答");
        feedback.setTraceId("trace-1");
        when(repository.findByTenantIdAndTraceIdAndRatingOrderByCreatedAtDesc(any(), any(), any(),
                any(Pageable.class))).thenReturn(List.of(feedback));

        AgentFeedbackListResponse response = service.listCurrentTenantFeedbacks(10, "trace-1", "DOWN");

        assertTrue(response.success());
        assertEquals("trace-1", response.feedbacks().get(0).traceId());
        assertEquals("DOWN", response.feedbacks().get(0).rating());
        verify(repository).findByTenantIdAndTraceIdAndRatingOrderByCreatedAtDesc(any(), any(), any(),
                any(Pageable.class));
    }

    @Test
    void summarizeCurrentTenantFeedbacksReturnsCountsAndLatestNegative() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        AgentFeedback latestNegative = new AgentFeedback();
        latestNegative.setFeedbackId("feedback-down");
        latestNegative.setTenantId("tenant-a");
        latestNegative.setRating("DOWN");
        when(repository.countByTenantId("tenant-a")).thenReturn(10L);
        when(repository.countByTenantIdAndRating("tenant-a", "UP")).thenReturn(7L);
        when(repository.countByTenantIdAndRating("tenant-a", "DOWN")).thenReturn(3L);
        when(repository.findFirstByTenantIdAndRatingOrderByCreatedAtDesc("tenant-a", "DOWN"))
                .thenReturn(Optional.of(latestNegative));

        AgentFeedbackSummaryResponse response = service.summarizeCurrentTenantFeedbacks();

        assertTrue(response.success());
        assertEquals(10L, response.totalCount());
        assertEquals(7L, response.upCount());
        assertEquals(3L, response.downCount());
        assertEquals(0.7D, response.positiveRate());
        assertEquals("feedback-down", response.latestNegative().feedbackId());
    }

    @Test
    void analyzeCurrentTenantFeedbacksDelegatesNegativeSamplesToAnalyzer() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        when(repository.countByTenantIdAndRating("tenant-a", "DOWN")).thenReturn(3L);
        List<AgentFeedback> feedbacks = List.of(
                feedback("f1", "DOWN", "答案不准确，算错了", "销售额是多少", "销售额为 10"));
        when(repository.findByTenantIdAndRatingOrderByCreatedAtDesc(any(), any(), any(Pageable.class)))
                .thenReturn(feedbacks);

        service.analyzeCurrentTenantFeedbacks();

        var pageCaptor = forClass(Pageable.class);
        verify(repository).findByTenantIdAndRatingOrderByCreatedAtDesc(any(), any(), pageCaptor.capture());
        assertEquals(25, pageCaptor.getValue().getPageSize());
        verify(insightAnalyzer).analyze(feedbacks, 3L);
    }

    @Test
    void getCurrentTenantDashboardAggregatesSummaryInsightsAndList() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        when(repository.countByTenantId("tenant-a")).thenReturn(1L);
        when(repository.countByTenantIdAndRating("tenant-a", "UP")).thenReturn(1L);
        when(repository.countByTenantIdAndRating("tenant-a", "DOWN")).thenReturn(0L);
        when(repository.findFirstByTenantIdAndRatingOrderByCreatedAtDesc("tenant-a", "DOWN"))
                .thenReturn(Optional.empty());
        when(repository.findByTenantIdAndRatingOrderByCreatedAtDesc(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(repository.findByTenantIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(feedback("f1", "UP", "很好", "问题", "回答")));

        AgentFeedbackDashboardResponse response = service.getCurrentTenantDashboard(10, null, null);

        assertTrue(response.success());
        assertEquals(1L, response.summary().totalCount());
        assertEquals(1, response.feedbacks().feedbacks().size());
        verify(insightAnalyzer).analyze(List.of(), 0L);
    }

    private void mockCurrentUser() {
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        when(securityContextHelper.getCurrentUserId()).thenReturn("user-a");
    }

    private AgentFeedback feedback(String feedbackId, String rating, String comment, String question, String answer) {
        AgentFeedback feedback = new AgentFeedback();
        feedback.setFeedbackId(feedbackId);
        feedback.setTenantId("tenant-a");
        feedback.setUserId("user-a");
        feedback.setRating(rating);
        feedback.setComment(comment);
        feedback.setQuestion(question);
        feedback.setAnswer(answer);
        return feedback;
    }
}
