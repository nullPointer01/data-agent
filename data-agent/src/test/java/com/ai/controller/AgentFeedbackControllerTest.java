package com.ai.controller;

import com.ai.agent.dto.AgentFeedbackDashboardResponse;
import com.ai.agent.dto.AgentFeedbackInsightResponse;
import com.ai.agent.dto.AgentFeedbackIssueResponse;
import com.ai.agent.dto.AgentFeedbackListResponse;
import com.ai.agent.dto.AgentFeedbackMutationResponse;
import com.ai.agent.dto.AgentFeedbackRequest;
import com.ai.agent.dto.AgentFeedbackResponse;
import com.ai.agent.dto.AgentFeedbackSummaryResponse;
import com.ai.security.auth.JwtTokenProvider;
import com.ai.security.SecurityContextHelper;
import com.ai.service.AgentFeedbackService;
import com.ai.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AgentFeedbackController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@AutoConfigureMockMvc(addFilters = false)
class AgentFeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentFeedbackService feedbackService;

    @MockBean
    private SecurityContextHelper securityContextHelper;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void saveFeedbackDelegatesToService() throws Exception {
        AgentFeedbackRequest request = new AgentFeedbackRequest("session-1", "trace-1",
                "UP", "问题", "回答", null);
        when(feedbackService.saveCurrentUserFeedback(any(AgentFeedbackRequest.class)))
                .thenReturn(AgentFeedbackMutationResponse.saved("feedback-1"));

        mockMvc.perform(post("/api/v1/agent-feedbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.feedbackId").value("feedback-1"));

        verify(feedbackService).saveCurrentUserFeedback(request);
    }

    @Test
    void listFeedbacksDelegatesToService() throws Exception {
        AgentFeedbackResponse feedback = new AgentFeedbackResponse("feedback-1",
                "tenant-a", "user-a", "session-1", "trace-1", "UP",
                "问题", "回答", null, null, null);
        when(feedbackService.listCurrentTenantFeedbacks(10, "trace-1", "DOWN"))
                .thenReturn(new AgentFeedbackListResponse(true, List.of(feedback)));

        mockMvc.perform(get("/api/v1/agent-feedbacks")
                        .param("limit", "10")
                        .param("traceId", "trace-1")
                        .param("rating", "DOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value("feedback-1"));

        verify(feedbackService).listCurrentTenantFeedbacks(10, "trace-1", "DOWN");
    }

    @Test
    void summaryDelegatesToService() throws Exception {
        when(feedbackService.summarizeCurrentTenantFeedbacks())
                .thenReturn(new AgentFeedbackSummaryResponse(true, 10L, 7L, 3L, 0.7D, null));

        mockMvc.perform(get("/api/v1/agent-feedbacks/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.totalCount").value(10))
                .andExpect(jsonPath("$.positiveRate").value(0.7D));

        verify(feedbackService).summarizeCurrentTenantFeedbacks();
    }

    @Test
    void dashboardDelegatesToService() throws Exception {
        AgentFeedbackSummaryResponse summary = new AgentFeedbackSummaryResponse(true, 10L, 7L, 3L, 0.7D, null);
        AgentFeedbackInsightResponse insights = new AgentFeedbackInsightResponse(true, 3, 3L, List.of(),
                List.of("复盘轨迹"));
        AgentFeedbackListResponse feedbacks = new AgentFeedbackListResponse(true, List.of());
        when(feedbackService.getCurrentTenantDashboard(20, "trace-1", "DOWN"))
                .thenReturn(new AgentFeedbackDashboardResponse(true, summary, insights, feedbacks));

        mockMvc.perform(get("/api/v1/agent-feedbacks/dashboard")
                        .param("limit", "20")
                        .param("traceId", "trace-1")
                        .param("rating", "DOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.summary.totalCount").value(10))
                .andExpect(jsonPath("$.insights.recommendations[0]").value("复盘轨迹"));

        verify(feedbackService).getCurrentTenantDashboard(20, "trace-1", "DOWN");
    }

    @Test
    void insightsDelegatesToService() throws Exception {
        AgentFeedbackIssueResponse issue = new AgentFeedbackIssueResponse("ACCURACY", "答案准确性",
                2L, 0.5D, "复盘事实依据", List.of());
        when(feedbackService.analyzeCurrentTenantFeedbacks())
                .thenReturn(new AgentFeedbackInsightResponse(true, 4, 4L, List.of(issue), List.of("复盘轨迹")));

        mockMvc.perform(get("/api/v1/agent-feedbacks/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.issues[0].issueType").value("ACCURACY"))
                .andExpect(jsonPath("$.recommendations[0]").value("复盘轨迹"));

        verify(feedbackService).analyzeCurrentTenantFeedbacks();
    }
}
