package com.ai.agent;

import com.ai.memory.MemoryManager;
import com.ai.memory.dto.MemoryContext;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.repository.AgentProfileRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.service.AgentExecutionTraceService;
import com.ai.service.AgentProfileService;
import com.ai.service.file.FileProcessingService;
import com.ai.service.MultiAgentRuntimeService;
import com.ai.service.SessionManager;
import com.ai.skill.SkillManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.ai.agent.specialist.AgentSpecialist;
import com.ai.agent.react.ReActAgent;
import com.ai.agent.orchestrator.OrchestratorAgent;
import com.ai.agent.specialist.AgentSpecialistRegistry;
import com.ai.agent.specialist.SpecialistFactory;
import com.ai.agent.tool.AgentConversationRecorder;

class CustomAgentRuntimeIntegrationTest {

    @Test
    void analyzeWithConfiguredAgentRunsThroughSpecialistFactory() {
        AgentProfile profile = profile();
        AgentProfileRepository repository = mock(AgentProfileRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        MemoryManager memoryManager = mock(MemoryManager.class);
        AtomicReference<AgentExecutionRequest> capturedRequest = new AtomicReference<>();
        AgentSpecialistRegistry registry = new AgentSpecialistRegistry(List.of(
                capturingSpecialist(capturedRequest),
                fallbackSpecialist()));
        SpecialistFactory specialistFactory = new SpecialistFactory(registry);
        AgentProfileService profileService = new AgentProfileService(repository, securityContextHelper, registry,
                specialistFactory, memoryManager);
        MultiAgentRuntimeService multiAgentRuntimeService = new MultiAgentRuntimeService(profileService,
                specialistFactory, memoryManager);
        OrchestratorAgent orchestratorAgent = mock(OrchestratorAgent.class);
        AgentRuntimeService runtimeService = new AgentRuntimeService(
                mock(SkillManager.class),
                mock(ReActAgent.class),
                multiAgentRuntimeService,
                orchestratorAgent,
                mock(SkillExecutionService.class),
                mock(AgentConversationRecorder.class),
                mock(AgentExecutionTraceService.class));
        DataAnalysisAgent agent = new DataAnalysisAgentImpl(
                mock(FileProcessingService.class),
                mock(SessionManager.class),
                runtimeService);
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-a");
        when(repository.findByAgentIdAndTenantId("agent-1", "tenant-a")).thenReturn(Optional.of(profile));
        when(memoryManager.buildContext(null, "帮我分析销售")).thenReturn(MemoryContext.empty());
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("帮我分析销售");
        request.setAgentId("agent-1");

        AnalysisResponse response = agent.analyze(request);

        assertEquals(true, response.isSuccess());
        assertEquals("custom-answer", response.getResult());
        assertEquals("agent:我的 Agent", response.getSkillUsed());
        assertEquals("model-1", capturedRequest.get().request().getModelId());
        assertEquals("agent-1", capturedRequest.get().request().getAgentId());
        assertNull(request.getModelId());
        verifyNoInteractions(orchestratorAgent);
    }

    private AgentProfile profile() {
        AgentProfile profile = new AgentProfile();
        profile.setAgentId("agent-1");
        profile.setTenantId("tenant-a");
        profile.setName("我的 Agent");
        profile.setType(AgentType.CHAT);
        profile.setModelId("model-1");
        profile.setEnabled(true);
        return profile;
    }

    private AgentSpecialist capturingSpecialist(AtomicReference<AgentExecutionRequest> capturedRequest) {
        return new AgentSpecialist() {
            @Override
            public AgentType type() {
                return AgentType.CHAT;
            }

            @Override
            public AnalysisResponse execute(AgentExecutionRequest request) {
                capturedRequest.set(request);
                return AnalysisResponse.ok("custom-answer");
            }
        };
    }

    private AgentSpecialist fallbackSpecialist() {
        return new AgentSpecialist() {
            @Override
            public AgentType type() {
                return AgentType.REACT;
            }

            @Override
            public AnalysisResponse execute(AgentExecutionRequest request) {
                return AnalysisResponse.ok("fallback-answer");
            }
        };
    }
}
