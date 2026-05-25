package com.ai.controller;

import com.ai.agent.dto.AgentProfileListResponse;
import com.ai.agent.dto.AgentProfileMutationResponse;
import com.ai.agent.dto.AgentProfileRequest;
import com.ai.agent.dto.AgentProfileResponse;
import com.ai.agent.dto.AgentTestRequest;
import com.ai.model.AnalysisResponse;
import com.ai.security.auth.JwtTokenProvider;
import com.ai.security.SecurityContextHelper;
import com.ai.service.AgentProfileService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = CustomAgentController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@AutoConfigureMockMvc(addFilters = false)
class CustomAgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentProfileService agentProfileService;

    @MockBean
    private SecurityContextHelper securityContextHelper;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void listReturnsCurrentUserAgents() throws Exception {
        AgentProfileResponse agent = new AgentProfileResponse("agent-1", "我的 Agent", "CHAT",
                null, null, null, null, null, true, "tenant-a", "user-a", null, null);
        when(agentProfileService.listMyAgents(true)).thenReturn(new AgentProfileListResponse(true, List.of(agent)));

        mockMvc.perform(get("/api/v1/my/agents").param("enabledOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.agents[0].agentId").value("agent-1"));
    }

    @Test
    void detailReturnsCurrentUserAgent() throws Exception {
        AgentProfileResponse agent = new AgentProfileResponse("agent-1", "我的 Agent", "CHAT",
                null, null, null, null, null, true, "tenant-a", "user-a", null, null);
        when(agentProfileService.getMyAgent("agent-1")).thenReturn(agent);

        mockMvc.perform(get("/api/v1/my/agents/agent-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-1"));
    }

    @Test
    void createDelegatesToCurrentUserService() throws Exception {
        AgentProfileRequest request = new AgentProfileRequest(null, "我的 Agent", "CHAT",
                null, null, null, null, null, true);
        when(agentProfileService.createMyAgent(any(AgentProfileRequest.class)))
                .thenReturn(AgentProfileMutationResponse.saved("agent-1"));

        mockMvc.perform(post("/api/v1/my/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-1"));
    }

    @Test
    void updateUsesCurrentUserScope() throws Exception {
        AgentProfileRequest request = new AgentProfileRequest("ignored", "我的 Agent", "CHAT",
                null, null, null, null, null, true);
        when(agentProfileService.updateMyAgent(any(), any(AgentProfileRequest.class)))
                .thenReturn(AgentProfileMutationResponse.saved("agent-1"));

        mockMvc.perform(put("/api/v1/my/agents/agent-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-1"));

        verify(agentProfileService).updateMyAgent("agent-1", request);
    }

    @Test
    void toggleUsesCurrentUserScope() throws Exception {
        when(agentProfileService.toggleMyAgent("agent-1")).thenReturn(AgentProfileMutationResponse.toggled(false));

        mockMvc.perform(put("/api/v1/my/agents/agent-1/enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void testUsesCurrentUserScope() throws Exception {
        AgentTestRequest request = new AgentTestRequest("解释一下这份数据", null, null, null);
        when(agentProfileService.testMyAgent(any(), any(AgentTestRequest.class)))
                .thenReturn(AnalysisResponse.ok("试运行结果"));

        mockMvc.perform(post("/api/v1/my/agents/agent-1/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("试运行结果"));

        verify(agentProfileService).testMyAgent("agent-1", request);
    }

    @Test
    void deleteUsesCurrentUserScope() throws Exception {
        when(agentProfileService.deleteMyAgent("agent-1")).thenReturn(AgentProfileMutationResponse.deleted());

        mockMvc.perform(delete("/api/v1/my/agents/agent-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
