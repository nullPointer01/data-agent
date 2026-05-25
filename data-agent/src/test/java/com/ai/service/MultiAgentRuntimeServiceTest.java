package com.ai.service;

import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.specialist.AgentSpecialist;
import com.ai.agent.specialist.AgentSpecialistRegistry;
import com.ai.agent.AgentType;
import com.ai.agent.specialist.SpecialistFactory;
import com.ai.memory.MemoryManager;
import com.ai.memory.MemorySource;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;
import com.ai.memory.dto.MemoryContext;
import com.ai.memory.dto.MemoryEntrySummary;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiAgentRuntimeServiceTest {

    @Test
    void executeDelegatesToResolvedSpecialistAndAppliesDefaultModel() {
        AgentProfileService profileService = mock(AgentProfileService.class);
        AgentProfile profile = new AgentProfile();
        profile.setName("Sales Agent");
        profile.setType(AgentType.CHAT);
        profile.setModelId("model-1");
        when(profileService.requireEnabledAgent("agent-1")).thenReturn(profile);
        AtomicReference<AnalysisRequest> capturedRequest = new AtomicReference<>();
        AtomicReference<MemoryContext> capturedMemory = new AtomicReference<>();
        MemoryManager memoryManager = mock(MemoryManager.class);
        when(memoryManager.buildContext(null, "hello")).thenReturn(memoryContext());
        MultiAgentRuntimeService service = new MultiAgentRuntimeService(profileService,
                new SpecialistFactory(new AgentSpecialistRegistry(List.of(
                        capturingSpecialist(AgentType.CHAT, capturedRequest, capturedMemory),
                        specialist(AgentType.REACT, new AtomicReference<>())))),
                memoryManager);
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("hello");

        AnalysisResponse response = service.execute("agent-1", request, "file");

        assertEquals("specialist-chat", response.getResult());
        assertEquals("agent:Sales Agent", response.getSkillUsed());
        assertEquals("model-1", capturedRequest.get().getModelId());
        assertEquals("张三", capturedMemory.get().userProfile().displayName());
        assertNull(request.getModelId());
    }

    private AgentSpecialist specialist(AgentType type, AtomicReference<AnalysisRequest> capturedRequest) {
        return new AgentSpecialist() {
            @Override
            public AgentType type() {
                return type;
            }

            @Override
            public AnalysisResponse execute(AgentExecutionRequest request) {
                capturedRequest.set(request.request());
                return AnalysisResponse.ok("specialist-" + type.name().toLowerCase());
            }
        };
    }

    private AgentSpecialist capturingSpecialist(AgentType type,
            AtomicReference<AnalysisRequest> capturedRequest,
            AtomicReference<MemoryContext> capturedMemory) {
        return new AgentSpecialist() {
            @Override
            public AgentType type() {
                return type;
            }

            @Override
            public AnalysisResponse execute(AgentExecutionRequest request) {
                capturedRequest.set(request.request());
                capturedMemory.set(request.memoryContext());
                return AnalysisResponse.ok("specialist-" + type.name().toLowerCase());
            }
        };
    }

    private MemoryContext memoryContext() {
        return new MemoryContext(
                "",
                List.of(new MemoryEntrySummary("m-1", MemoryTier.SHORT_TERM, MemoryType.SUMMARY,
                        MemorySource.SYSTEM_GENERATED, "用户关注复购率", 0.8D, null)),
                "",
                new UserMemoryProfileSnapshotResponse("张三", null, null, null, null, null,
                        List.of(), List.of(), List.of(), 0.8D));
    }
}
