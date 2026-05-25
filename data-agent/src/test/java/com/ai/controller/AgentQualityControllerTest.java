package com.ai.controller;

import com.ai.agent.dto.AgentQualityAttributionResponse;
import com.ai.agent.dto.AgentQualityDashboardResponse;
import com.ai.agent.dto.AgentQualityRiskResponse;
import com.ai.agent.dto.AgentQualityTrendPointResponse;
import com.ai.agent.dto.AgentReasoningAcceptanceCheck;
import com.ai.agent.dto.AgentReasoningHealthResponse;
import com.ai.security.auth.JwtTokenProvider;
import com.ai.security.SecurityContextHelper;
import com.ai.service.AgentQualityService;
import com.ai.service.AgentReasoningHealthService;
import com.ai.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AgentQualityController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@AutoConfigureMockMvc(addFilters = false)
class AgentQualityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentQualityService agentQualityService;

    @MockBean
    private AgentReasoningHealthService reasoningHealthService;

    @MockBean
    private SecurityContextHelper securityContextHelper;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void dashboardDelegatesToService() throws Exception {
        AgentQualityRiskResponse risk = new AgentQualityRiskResponse("LOW_SUCCESS_RATE",
                "成功率偏低", "HIGH", "需要复盘失败轨迹", "80%");
        AgentQualityAttributionResponse attribution = new AgentQualityAttributionResponse("MODEL_CALL",
                "模型调用异常", "MEDIUM", 2L, 0.4D, "trace-1", "问题", "检查模型配置");
        when(agentQualityService.getCurrentTenantDashboard(50))
                .thenReturn(new AgentQualityDashboardResponse(true, 10, 82, "HEALTHY",
                        0.9D, 0.8D, 1_200L, 0.1D, 5L, 1L,
                        List.of(attribution), List.of(risk), List.of("复盘失败轨迹"), List.of(),
                        List.<AgentQualityTrendPointResponse>of()));

        mockMvc.perform(get("/api/v1/agent-quality/dashboard").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.qualityScore").value(82))
                .andExpect(jsonPath("$.attributions[0].causeCode").value("MODEL_CALL"))
                .andExpect(jsonPath("$.risks[0].riskCode").value("LOW_SUCCESS_RATE"))
                .andExpect(jsonPath("$.recommendations[0]").value("复盘失败轨迹"));

        verify(agentQualityService).getCurrentTenantDashboard(50);
    }

    @Test
    void reasoningHealthDelegatesToService() throws Exception {
        when(reasoningHealthService.getCurrentTenantHealth(50))
                .thenReturn(new AgentReasoningHealthResponse(true, false, 1,
                        true, true, true, true, true,
                        1L, 0L, 300L, 0L, 0L, 0L, 0D,
                        List.of(new AgentReasoningAcceptanceCheck("TRACE_OBSERVABILITY", "执行轨迹观测",
                                "PASSED", true, "最近已采集 1 条执行轨迹")),
                        List.of()));

        mockMvc.perform(get("/api/v1/agent-quality/reasoning-health").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(true))
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.acceptanceChecks[0].key").value("TRACE_OBSERVABILITY"));

        verify(reasoningHealthService).getCurrentTenantHealth(50);
    }
}
