package com.ai.controller;

import com.ai.agent.dto.AgentProfileListResponse;
import com.ai.agent.dto.AgentProfileMutationResponse;
import com.ai.agent.dto.AgentProfileRequest;
import com.ai.agent.dto.AgentProfileResponse;
import com.ai.agent.dto.AgentRegistryCapabilityResponse;
import com.ai.agent.dto.AgentRegistryEntryResponse;
import com.ai.agent.dto.AgentRegistryResponse;
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
        controllers = AgentProfileController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@AutoConfigureMockMvc(addFilters = false)
class AgentProfileControllerTest {

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
    void listReturnsAgentProfiles() throws Exception {
        AgentProfileResponse agent = new AgentProfileResponse(
                "agent-1", "分析 Agent", "REACT", null, null, null, null, null, true,
                "tenant-a", "user-a", null, null);
        when(agentProfileService.listAgents(true)).thenReturn(new AgentProfileListResponse(true, List.of(agent)));

        mockMvc.perform(get("/api/v1/agents").param("enabledOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.agents[0].agentId").value("agent-1"));
    }

    @Test
    void detailReturnsAgentProfile() throws Exception {
        AgentProfileResponse agent = new AgentProfileResponse(
                "agent-1", "分析 Agent", "REACT", null, null, null, null, null, true,
                "tenant-a", "user-a", null, null);
        when(agentProfileService.getAgent("agent-1")).thenReturn(agent);

        mockMvc.perform(get("/api/v1/agents/agent-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-1"));
    }

    @Test
    void registryReturnsAgentRegistrySnapshot() throws Exception {
        when(agentProfileService.getRegistry()).thenReturn(new AgentRegistryResponse(true, 1, 1, 1,
                List.of(new AgentRegistryCapabilityResponse("REACT", "ReAct 工具 Agent",
                        "tool_reasoning", "适合复杂推理", true, 1L, 1L)),
                List.of(new AgentRegistryEntryResponse("system:REACT", "ReAct 工具 Agent",
                        "REACT", "SYSTEM", "tool_reasoning", "适合复杂推理",
                        true, true, null, null, null, List.of("system")))));

        mockMvc.perform(get("/api/v1/agents/registry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.capabilities[0].type").value("REACT"))
                .andExpect(jsonPath("$.entries[0].registryId").value("system:REACT"));
    }

    @Test
    void createDelegatesToService() throws Exception {
        AgentProfileRequest request = new AgentProfileRequest(
                null, "数据 Agent", "DATA", null, null, null, null, "datasource-1", true);
        when(agentProfileService.createAgent(any(AgentProfileRequest.class)))
                .thenReturn(AgentProfileMutationResponse.saved("agent-1"));

        mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.agentId").value("agent-1"));
    }

    @Test
    void updateUsesPathAgentId() throws Exception {
        AgentProfileRequest request = new AgentProfileRequest(
                "ignored", "对话 Agent", "CHAT", null, null, null, null, null, true);
        when(agentProfileService.updateAgent(any(), any(AgentProfileRequest.class)))
                .thenReturn(AgentProfileMutationResponse.saved("agent-1"));

        mockMvc.perform(put("/api/v1/agents/agent-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-1"));

        verify(agentProfileService).updateAgent("agent-1", request);
    }

    @Test
    void toggleDelegatesToService() throws Exception {
        when(agentProfileService.toggle("agent-1")).thenReturn(AgentProfileMutationResponse.toggled(false));

        mockMvc.perform(put("/api/v1/agents/agent-1/enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void testDelegatesToService() throws Exception {
        AgentTestRequest request = new AgentTestRequest("帮我分析销售额", "file", null, null);
        when(agentProfileService.testAgent(any(), any(AgentTestRequest.class)))
                .thenReturn(AnalysisResponse.ok("测试结果"));

        mockMvc.perform(post("/api/v1/agents/agent-1/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result").value("测试结果"));

        verify(agentProfileService).testAgent("agent-1", request);
    }

    @Test
    void deleteDelegatesToService() throws Exception {
        when(agentProfileService.delete("agent-1")).thenReturn(AgentProfileMutationResponse.deleted());

        mockMvc.perform(delete("/api/v1/agents/agent-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
