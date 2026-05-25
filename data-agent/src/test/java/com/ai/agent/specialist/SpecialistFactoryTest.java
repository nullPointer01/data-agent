package com.ai.agent.specialist;

import com.ai.memory.dto.MemoryContext;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.ai.agent.AgentType;
import com.ai.agent.AgentExecutionRequest;

class SpecialistFactoryTest {

    @Test
    void buildRequestAppliesProfileModelWithoutMutatingOriginalRequest() {
        SpecialistFactory factory = new SpecialistFactory(new AgentSpecialistRegistry(List.of(specialist(AgentType.CHAT))));
        AgentProfile profile = profile(AgentType.CHAT);
        profile.setAgentId("agent-1");
        profile.setModelId("model-1");
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("hello");

        AgentExecutionRequest executionRequest = factory.buildRequest(profile, request, null, null,
                MemoryContext.empty());

        assertEquals("model-1", executionRequest.request().getModelId());
        assertEquals("agent-1", executionRequest.request().getAgentId());
        assertNull(request.getModelId());
        assertNull(request.getAgentId());
    }

    @Test
    void executeDelegatesToResolvedSpecialist() {
        AtomicReference<AgentExecutionRequest> capturedRequest = new AtomicReference<>();
        SpecialistFactory factory = new SpecialistFactory(new AgentSpecialistRegistry(List.of(
                capturingSpecialist(AgentType.CHAT, capturedRequest))));
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("hello");

        AnalysisResponse response = factory.execute(profile(AgentType.CHAT), request, "file", null,
                MemoryContext.empty());

        assertEquals(true, response.isSuccess());
        assertEquals("CHAT", response.getResult());
        assertEquals("file", capturedRequest.get().fileContent());
    }

    @Test
    void requireSpecialistRejectsUnregisteredType() {
        SpecialistFactory factory = new SpecialistFactory(new AgentSpecialistRegistry(List.of(specialist(AgentType.CHAT))));

        assertThrows(IllegalArgumentException.class, () -> factory.requireSpecialist(AgentType.DATA));
    }

    private AgentProfile profile(AgentType type) {
        AgentProfile profile = new AgentProfile();
        profile.setName(type.name());
        profile.setType(type);
        profile.setEnabled(true);
        return profile;
    }

    private AgentSpecialist specialist(AgentType type) {
        return capturingSpecialist(type, new AtomicReference<>());
    }

    private AgentSpecialist capturingSpecialist(AgentType type, AtomicReference<AgentExecutionRequest> capturedRequest) {
        return new AgentSpecialist() {
            @Override
            public AgentType type() {
                return type;
            }

            @Override
            public AnalysisResponse execute(AgentExecutionRequest request) {
                capturedRequest.set(request);
                return AnalysisResponse.ok(type.name());
            }
        };
    }
}
