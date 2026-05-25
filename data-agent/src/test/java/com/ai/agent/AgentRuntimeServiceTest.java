package com.ai.agent;

import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.model.ConversationSession;
import com.ai.service.AgentExecutionTraceService;
import com.ai.service.MultiAgentRuntimeService;
import com.ai.skill.SkillManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.ai.agent.orchestrator.OrchestratorAgent;
import com.ai.agent.tool.AgentConversationRecorder;
import com.ai.agent.orchestrator.OrchestratorResult;
import com.ai.agent.react.ReActAgent;
import com.ai.agent.orchestrator.OrchestratorExecutionResult;

class AgentRuntimeServiceTest {

    @Test
    void executeRoutesCommandWhenCommandMatches() {
        SkillManager skillManager = mock(SkillManager.class);
        ReActAgent reActAgent = mock(ReActAgent.class);
        AgentConversationRecorder recorder = mock(AgentConversationRecorder.class);
        AgentRuntimeService service = newService(skillManager, reActAgent, mock(MultiAgentRuntimeService.class),
                mock(OrchestratorAgent.class), mock(SkillExecutionService.class), recorder);
        AnalysisRequest request = request("/help");
        ConversationSession session = new ConversationSession("session-1");
        when(skillManager.processWithCommand("/help", null)).thenReturn("command-result");

        AnalysisResponse response = service.execute(new AgentExecutionContext(request, session, null));

        assertEquals("command-result", response.getResult());
        assertEquals("command", response.getSkillUsed());
        verify(recorder).recordSessionConversation(session, request, "command-result", "command", null);
        verifyNoInteractions(reActAgent);
    }

    @Test
    void executeFallsBackToReActWhenSkillServiceReturnsNull() {
        SkillManager skillManager = mock(SkillManager.class);
        ReActAgent reActAgent = mock(ReActAgent.class);
        SkillExecutionService skillExecutionService = mock(SkillExecutionService.class);
        OrchestratorAgent orchestratorAgent = mock(OrchestratorAgent.class);
        AnalysisRequest request = request("hello");
        OrchestratorResult orchestratorResult = orchestratorResult(request, AnalysisResponse.ok("orchestrator-result"));
        AgentRuntimeService service = newService(skillManager, reActAgent, mock(MultiAgentRuntimeService.class),
                orchestratorAgent, skillExecutionService, mock(AgentConversationRecorder.class));
        request.setSkillId("missing");
        when(skillExecutionService.execute(request, null, null)).thenReturn(null);
        when(orchestratorAgent.executeStructured(request, null, null)).thenReturn(orchestratorResult);

        AnalysisResponse response = service.execute(new AgentExecutionContext(request, null, null));

        assertEquals("orchestrator-result", response.getResult());
        verify(orchestratorAgent).executeStructured(request, null, null);
        verifyNoInteractions(reActAgent);
    }

    @Test
    void executeRoutesConfiguredAgentBeforeSkill() {
        SkillManager skillManager = mock(SkillManager.class);
        MultiAgentRuntimeService multiAgentRuntimeService = mock(MultiAgentRuntimeService.class);
        SkillExecutionService skillExecutionService = mock(SkillExecutionService.class);
        AgentRuntimeService service = newService(skillManager, mock(ReActAgent.class), multiAgentRuntimeService,
                mock(OrchestratorAgent.class), skillExecutionService, mock(AgentConversationRecorder.class));
        AnalysisRequest request = request("hello");
        request.setAgentId("agent-1");
        request.setSkillId("skill-1");
        when(multiAgentRuntimeService.execute("agent-1", request, "file")).thenReturn(AnalysisResponse.ok("agent"));

        AnalysisResponse response = service.execute(new AgentExecutionContext(request, null, "file"));

        assertEquals("agent", response.getResult());
        verify(multiAgentRuntimeService).execute("agent-1", request, "file");
        verifyNoInteractions(skillExecutionService);
    }

    @Test
    void executeRoutesDefaultRequestThroughOrchestrator() {
        SkillManager skillManager = mock(SkillManager.class);
        OrchestratorAgent orchestratorAgent = mock(OrchestratorAgent.class);
        AgentExecutionTraceService traceService = mock(AgentExecutionTraceService.class);
        AgentConversationRecorder recorder = mock(AgentConversationRecorder.class);
        AgentRuntimeService service = newService(skillManager, mock(ReActAgent.class),
                mock(MultiAgentRuntimeService.class), orchestratorAgent, mock(SkillExecutionService.class),
                recorder, traceService);
        AnalysisRequest request = request("分析销售趋势");
        ConversationSession session = new ConversationSession("session-1");
        OrchestratorResult orchestratorResult = orchestratorResult(request, AnalysisResponse.ok("orchestrated"));
        when(orchestratorAgent.executeStructured(request, "file", session)).thenReturn(orchestratorResult);
        when(traceService.record(request, session, orchestratorResult)).thenReturn("trace-1");

        AnalysisResponse response = service.execute(new AgentExecutionContext(request, session, "file"));

        assertEquals("orchestrated", response.getResult());
        assertEquals("trace-1", response.getTraceId());
        verify(orchestratorAgent).executeStructured(request, "file", session);
        verify(traceService).record(request, session, orchestratorResult);
        verify(recorder).recordAnalysisConversation(session, request, "orchestrated", "orchestrator", null);
    }

    @Test
    void executeRoutesConfiguredAgentAndPersistsConversation() {
        SkillManager skillManager = mock(SkillManager.class);
        MultiAgentRuntimeService multiAgentRuntimeService = mock(MultiAgentRuntimeService.class);
        AgentConversationRecorder recorder = mock(AgentConversationRecorder.class);
        AgentRuntimeService service = newService(skillManager, mock(ReActAgent.class), multiAgentRuntimeService,
                mock(OrchestratorAgent.class), mock(SkillExecutionService.class), recorder);
        AnalysisRequest request = request("hello");
        request.setAgentId("agent-1");
        ConversationSession session = new ConversationSession("session-1");
        AnalysisResponse responseBody = AnalysisResponse.ok("agent");
        responseBody.setSkillUsed("agent:Sales Agent");
        when(multiAgentRuntimeService.execute("agent-1", request, null)).thenReturn(responseBody);

        AnalysisResponse response = service.execute(new AgentExecutionContext(request, session, null));

        assertEquals("agent", response.getResult());
        verify(multiAgentRuntimeService).execute("agent-1", request, null);
        verify(recorder).recordAnalysisConversation(session, request, "agent", "agent:Sales Agent", null);
    }

    private AgentRuntimeService newService(SkillManager skillManager,
            ReActAgent reActAgent,
            MultiAgentRuntimeService multiAgentRuntimeService,
            OrchestratorAgent orchestratorAgent,
            SkillExecutionService skillExecutionService,
            AgentConversationRecorder recorder) {
        return newService(skillManager, reActAgent, multiAgentRuntimeService, orchestratorAgent,
                skillExecutionService, recorder, mock(AgentExecutionTraceService.class));
    }

    private AgentRuntimeService newService(SkillManager skillManager,
            ReActAgent reActAgent,
            MultiAgentRuntimeService multiAgentRuntimeService,
            OrchestratorAgent orchestratorAgent,
            SkillExecutionService skillExecutionService,
            AgentConversationRecorder recorder,
            AgentExecutionTraceService traceService) {
        return new AgentRuntimeService(skillManager, reActAgent, multiAgentRuntimeService, orchestratorAgent,
                skillExecutionService, recorder, traceService);
    }

    private AnalysisRequest request(String question) {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion(question);
        return request;
    }

    private OrchestratorResult orchestratorResult(AnalysisRequest request, AnalysisResponse response) {
        OrchestratorExecutionResult executionResult = new OrchestratorExecutionResult(null, response, false);
        OrchestratorResult result = new OrchestratorResult(response.isSuccess(), response.getResult(),
                AgentType.REACT, "", executionResult, null, java.util.Map.of(), null);
        result.setOriginalQuery(request.getQuestion());
        result.setError(response.getError());
        result.setDurationMs(1L);
        return result;
    }
}
