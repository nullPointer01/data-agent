package com.ai.controller;

import com.ai.agent.dto.AgentExecutionTraceListResponse;
import com.ai.agent.dto.AgentExecutionTraceResponse;
import com.ai.security.auth.JwtTokenProvider;
import com.ai.security.SecurityContextHelper;
import com.ai.service.AgentExecutionTraceService;
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
        controllers = AgentExecutionTraceController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@AutoConfigureMockMvc(addFilters = false)
class AgentExecutionTraceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentExecutionTraceService traceService;

    @MockBean
    private SecurityContextHelper securityContextHelper;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void listTracesReturnsTenantTraces() throws Exception {
        AgentExecutionTraceResponse trace = trace("trace-1");
        when(traceService.listCurrentTenant(10, "user-a"))
                .thenReturn(new AgentExecutionTraceListResponse(true, List.of(trace)));

        mockMvc.perform(get("/api/v1/agent-traces").param("limit", "10").param("userId", "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traces[0].traceId").value("trace-1"));
    }

    @Test
    void detailDelegatesToTraceService() throws Exception {
        when(traceService.getCurrentTenantTrace("trace-1")).thenReturn(trace("trace-1"));

        mockMvc.perform(get("/api/v1/agent-traces/trace-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value("trace-1"));

        verify(traceService).getCurrentTenantTrace("trace-1");
    }

    private AgentExecutionTraceResponse trace(String traceId) {
        return new AgentExecutionTraceResponse(traceId, "tenant-a", "user-a", "session-1",
                "内置 ReAct", "REACT", "DATA_ANALYSIS", "TOOL_ASSISTED", true, false,
                1, 12L, "分析销售趋势", "路由原因", "", "{}", "[]", "{}", null);
    }
}
