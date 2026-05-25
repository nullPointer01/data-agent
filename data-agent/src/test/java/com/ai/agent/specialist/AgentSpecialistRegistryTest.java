package com.ai.agent.specialist;

import com.ai.model.AnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.ai.agent.AgentType;
import com.ai.agent.AgentExecutionRequest;

class AgentSpecialistRegistryTest {

    @Test
    void resolveFallsBackToReactSpecialist() {
        AgentSpecialist reactSpecialist = specialist(AgentType.REACT);
        AgentSpecialistRegistry registry = new AgentSpecialistRegistry(List.of(reactSpecialist));

        AgentSpecialist resolved = registry.resolve(AgentType.CHAT);

        assertEquals(AgentType.REACT, resolved.type());
    }

    @Test
    void exposesRegisteredRuntimeTypes() {
        AgentSpecialistRegistry registry = new AgentSpecialistRegistry(List.of(
                specialist(AgentType.REACT),
                specialist(AgentType.DATA)));

        assertEquals(2, registry.size());
        assertTrue(registry.findByType(AgentType.DATA).isPresent());
        assertTrue(registry.isRuntimeAvailable(AgentType.REACT));
        assertFalse(registry.isRuntimeAvailable(AgentType.CHAT));
        assertTrue(registry.registeredTypes().contains(AgentType.DATA));
    }

    private AgentSpecialist specialist(AgentType type) {
        return new AgentSpecialist() {
            @Override
            public AgentType type() {
                return type;
            }

            @Override
            public AnalysisResponse execute(AgentExecutionRequest request) {
                return AnalysisResponse.ok(type.name());
            }
        };
    }
}
