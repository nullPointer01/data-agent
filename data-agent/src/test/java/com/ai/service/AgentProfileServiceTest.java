package com.ai.service;

import com.ai.agent.AgentType;
import com.ai.agent.specialist.AgentSpecialist;
import com.ai.agent.specialist.AgentSpecialistRegistry;
import com.ai.agent.specialist.SpecialistFactory;
import com.ai.agent.dto.AgentProfileMutationResponse;
import com.ai.agent.dto.AgentProfileRequest;
import com.ai.agent.dto.AgentTestRequest;
import com.ai.agent.dto.AgentRegistryResponse;
import com.ai.memory.MemoryManager;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisResponse;
import com.ai.repository.AgentProfileRepository;
import com.ai.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ai.agent.AgentExecutionRequest;

class AgentProfileServiceTest {

    private final AgentProfileRepository repository = mock(AgentProfileRepository.class);
    private final SecurityContextHelper security = mock(SecurityContextHelper.class);
    private final AgentSpecialistRegistry registry = new AgentSpecialistRegistry(List.of(
            specialist(AgentType.REACT),
            specialist(AgentType.DATA),
            specialist(AgentType.KNOWLEDGE),
            specialist(AgentType.CHART),
            specialist(AgentType.REPORT),
            specialist(AgentType.CHAT)));
    private final AgentProfileService service = new AgentProfileService(repository, security, registry,
            new SpecialistFactory(registry), mock(MemoryManager.class));

    @Test
    void savesNewAgentWithTenantOwnership() {
        when(security.getCurrentTenantId()).thenReturn("tenant-a");
        when(security.getCurrentUserId()).thenReturn("user-a");
        when(repository.save(any(AgentProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentProfileRequest request = new AgentProfileRequest(
                null, "Sales Agent", "data", null, null, null, null, "datasource-1", true);

        AgentProfileMutationResponse result = service.createAgent(request);

        var captor = forClass(AgentProfile.class);
        verify(repository).save(captor.capture());
        AgentProfile saved = captor.getValue();

        assertEquals(true, result.success());
        assertEquals("tenant-a", saved.getTenantId());
        assertEquals(AgentType.DATA, saved.getType());
        assertEquals("datasource-1", saved.getDatasourceId());
    }

    @Test
    void rejectsUnsupportedAgentType() {
        AgentProfileRequest request = new AgentProfileRequest(
                null, "Bad Agent", "UNKNOWN", null, null, null, null, null, true);

        assertThrows(IllegalArgumentException.class, () -> service.createAgent(request));
    }

    @Test
    void rejectsDataAgentWithoutDatasource() {
        AgentProfileRequest request = new AgentProfileRequest(
                null, "Data Agent", "DATA", null, null, null, null, null, true);

        assertThrows(IllegalArgumentException.class, () -> service.createAgent(request));
    }

    @Test
    void updatesExistingAgentByPathId() {
        when(security.getCurrentTenantId()).thenReturn("tenant-a");
        AgentProfile profile = new AgentProfile();
        profile.setAgentId("agent-1");
        profile.setTenantId("tenant-a");
        profile.setType(AgentType.REACT);
        when(repository.findByAgentIdAndTenantId("agent-1", "tenant-a")).thenReturn(Optional.of(profile));
        when(repository.save(any(AgentProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentProfileRequest request = new AgentProfileRequest(
                "ignored-agent", "Chat Agent", "CHAT", "  assistant  ", null, "model-1", "skill-1", "datasource-1", true);

        service.updateAgent("agent-1", request);

        var captor = forClass(AgentProfile.class);
        verify(repository).save(captor.capture());
        AgentProfile saved = captor.getValue();

        assertEquals("agent-1", saved.getAgentId());
        assertEquals(AgentType.CHAT, saved.getType());
        assertEquals("assistant", saved.getDescription());
        assertNull(saved.getSkillId());
        assertNull(saved.getDatasourceId());
    }

    @Test
    void listMyAgentsUsesCurrentUserScope() {
        when(security.getCurrentTenantId()).thenReturn("tenant-a");
        when(security.getCurrentUserId()).thenReturn("user-a");
        AgentProfile profile = profile("agent-1", "我的 Agent", AgentType.CHAT, true);
        profile.setCreatedBy("user-a");
        when(repository.findByTenantIdAndCreatedByAndEnabledTrueOrderByUpdatedAtDesc("tenant-a", "user-a"))
                .thenReturn(List.of(profile));

        var response = service.listMyAgents(true);

        assertEquals(true, response.success());
        assertEquals("agent-1", response.agents().get(0).agentId());
    }

    @Test
    void updateMyAgentRequiresOwner() {
        when(security.getCurrentTenantId()).thenReturn("tenant-a");
        when(security.getCurrentUserId()).thenReturn("user-a");
        when(repository.findByAgentIdAndTenantIdAndCreatedBy("agent-1", "tenant-a", "user-a"))
                .thenReturn(Optional.empty());
        AgentProfileRequest request = new AgentProfileRequest(null, "我的 Agent", "CHAT",
                null, null, null, null, null, true);

        assertThrows(IllegalArgumentException.class, () -> service.updateMyAgent("agent-1", request));
    }

    @Test
    void testMyAgentExecutesConfiguredSpecialist() {
        when(security.getCurrentTenantId()).thenReturn("tenant-a");
        when(security.getCurrentUserId()).thenReturn("user-a");
        AgentProfile profile = profile("agent-1", "我的 Agent", AgentType.CHAT, true);
        profile.setCreatedBy("user-a");
        when(repository.findByAgentIdAndTenantIdAndCreatedBy("agent-1", "tenant-a", "user-a"))
                .thenReturn(Optional.of(profile));

        AnalysisResponse response = service.testMyAgent("agent-1",
                new AgentTestRequest("你好", null, null, null));

        assertEquals(true, response.isSuccess());
        assertEquals("CHAT", response.getResult());
        assertEquals("agent:我的 Agent", response.getSkillUsed());
    }

    @Test
    void rejectsDisabledAgentAtRuntime() {
        when(security.getCurrentTenantId()).thenReturn("tenant-a");
        AgentProfile profile = new AgentProfile();
        profile.setEnabled(false);
        when(repository.findByAgentIdAndTenantId("agent-1", "tenant-a")).thenReturn(Optional.of(profile));

        assertThrows(IllegalArgumentException.class, () -> service.requireEnabledAgent("agent-1"));
    }

    @Test
    void getRegistryCombinesSystemSpecialistsAndTenantAgents() {
        when(security.getCurrentTenantId()).thenReturn("tenant-a");
        AgentProfile dataAgent = profile("agent-1", "销售数据专家", AgentType.DATA, true);
        dataAgent.setDatasourceId("datasource-1");
        AgentProfile chatAgent = profile("agent-2", "客服助手", AgentType.CHAT, false);
        when(repository.findByTenantIdOrderByUpdatedAtDesc("tenant-a")).thenReturn(List.of(dataAgent, chatAgent));

        AgentRegistryResponse response = service.getRegistry();

        assertEquals(true, response.success());
        assertEquals(6, response.systemSpecialistCount());
        assertEquals(2, response.tenantAgentCount());
        assertEquals(1, response.enabledTenantAgentCount());
        assertEquals(7, response.capabilities().size());
        assertEquals(8, response.entries().size());
        assertEquals(true, response.entries().stream()
                .filter(entry -> "tenant:agent-2".equals(entry.registryId()))
                .findFirst()
                .orElseThrow()
                .runtimeAvailable());
    }

    private AgentProfile profile(String agentId, String name, AgentType type, boolean enabled) {
        AgentProfile profile = new AgentProfile();
        profile.setAgentId(agentId);
        profile.setTenantId("tenant-a");
        profile.setName(name);
        profile.setType(type);
        profile.setEnabled(enabled);
        return profile;
    }

    private AgentSpecialist specialist(AgentType type) {
        return new AgentSpecialist() {
            @Override
            public AgentType type() {
                return type;
            }

            @Override
            public AnalysisResponse execute(com.ai.agent.AgentExecutionRequest request) {
                return AnalysisResponse.ok(type.name());
            }
        };
    }
}
