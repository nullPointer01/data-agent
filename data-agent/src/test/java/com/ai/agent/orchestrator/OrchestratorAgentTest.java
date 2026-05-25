package com.ai.agent.orchestrator;

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
import com.ai.service.AgentProfileService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.ai.agent.react.ReActAgent;
import com.ai.agent.AgentType;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.specialist.AgentSpecialist;
import com.ai.agent.specialist.AgentSpecialistRegistry;
import com.ai.agent.specialist.SpecialistFactory;
import com.ai.agent.IntentAnalyzer;
import com.ai.agent.TaskComplexityClassifier;
import com.ai.agent.CollaborationManager;
import com.ai.agent.ResultIntegrator;

class OrchestratorAgentTest {

    @Test
    void executeRoutesDataQuestionToConfiguredDataSpecialist() {
        AgentProfileService profileService = mock(AgentProfileService.class);
        ReActAgent reActAgent = mock(ReActAgent.class);
        AgentProfile dataProfile = profile("销售数据专家", AgentType.DATA);
        when(profileService.listEnabledProfiles()).thenReturn(List.of(dataProfile));
        AgentSpecialist dataSpecialist = specialist(AgentType.DATA, "data-answer");
        OrchestratorAgent orchestrator = newOrchestrator(profileService, reActAgent, dataSpecialist);
        AnalysisRequest request = request("分析销售趋势");

        AnalysisResponse response = orchestrator.execute(request, "file", null);

        assertEquals("data-answer", response.getResult());
        assertEquals("orchestrator:销售数据专家", response.getSkillUsed());
        assertEquals("orchestrator", response.getThinkingSteps().get(0).getType());
        verifyNoInteractions(reActAgent);
    }

    @Test
    void executeFallsBackToBuiltInReActWhenNoProfileMatches() {
        AgentProfileService profileService = mock(AgentProfileService.class);
        ReActAgent reActAgent = mock(ReActAgent.class);
        when(profileService.listEnabledProfiles()).thenReturn(List.of());
        when(reActAgent.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AnalysisResponse.ok("react-answer"));
        OrchestratorAgent orchestrator = newOrchestrator(profileService, reActAgent);
        AnalysisRequest request = request("复杂问题需要推理");

        AnalysisResponse response = orchestrator.execute(request, null, null);

        assertEquals("react-answer", response.getResult());
        assertEquals("orchestrator:react", response.getSkillUsed());
        assertTrue(response.getThinkingSteps().get(0).getContent().contains("内置 ReAct"));
        verify(reActAgent).execute(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.contains("当前编排任务"));
    }

    @Test
    void executeWithTraceReturnsStructuredFallbackMetadata() {
        AgentProfileService profileService = mock(AgentProfileService.class);
        ReActAgent reActAgent = mock(ReActAgent.class);
        when(profileService.listEnabledProfiles()).thenReturn(List.of());
        when(reActAgent.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AnalysisResponse.ok("react-answer"));
        OrchestratorAgent orchestrator = newOrchestrator(profileService, reActAgent);
        AnalysisRequest request = request("复杂问题需要推理");

        OrchestratorExecutionResult result = orchestrator.executeWithTrace(request, null, null);

        assertEquals("react-answer", result.response().getResult());
        assertEquals(AgentType.REACT, result.selectedType());
        assertEquals("内置 ReAct", result.selectedAgentName());
        assertTrue(result.fallbackUsed());
    }

    @Test
    void executeStructuredRoutesKnowledgeTaskToSystemKnowledgeSpecialist() {
        AgentProfileService profileService = mock(AgentProfileService.class);
        ReActAgent reActAgent = mock(ReActAgent.class);
        when(profileService.listEnabledProfiles()).thenReturn(List.of());
        when(reActAgent.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AnalysisResponse.ok("综合答案"));
        AtomicReference<AgentExecutionRequest> capturedRequest = new AtomicReference<>();
        AgentSpecialist knowledgeSpecialist = capturingSpecialist(AgentType.KNOWLEDGE, "知识依据", capturedRequest);
        OrchestratorAgent orchestrator = newOrchestrator(profileService, reActAgent, knowledgeSpecialist);
        AnalysisRequest request = request("检索知识库里的报销制度");

        OrchestratorResult result = orchestrator.executeStructured(request, null, null);

        assertTrue(result.success());
        assertEquals(2, result.executionResult().taskResults().size());
        assertEquals(AgentType.KNOWLEDGE, result.selectedType());
        assertEquals("多专家协作", result.selectedAgentName());
        assertEquals("知识检索 Agent", result.executionResult().taskResults().get(0).specialistId());
        assertEquals(AgentType.KNOWLEDGE, capturedRequest.get().profile().getType());
        assertEquals("综合答案", result.finalAnswer());
    }

    @Test
    void executeStructuredReturnsPlanAndSharedContext() {
        AgentProfileService profileService = mock(AgentProfileService.class);
        ReActAgent reActAgent = mock(ReActAgent.class);
        AgentProfile dataProfile = profile("销售数据专家", AgentType.DATA);
        when(profileService.listEnabledProfiles()).thenReturn(List.of(dataProfile));
        AgentSpecialist dataSpecialist = specialist(AgentType.DATA, "data-answer");
        OrchestratorAgent orchestrator = newOrchestrator(profileService, reActAgent, dataSpecialist);
        AnalysisRequest request = request("分析销售趋势");

        OrchestratorResult result = orchestrator.executeStructured(request, "file", null);

        assertTrue(result.success());
        assertEquals("data-answer", result.finalAnswer());
        assertEquals(AgentType.DATA, result.selectedType());
        assertTrue(result.orchestrationPlan().hasTasks());
        assertEquals("data-answer", result.sharedContext().get("final_answer"));
        assertEquals("销售数据专家", result.sharedContext().get("selected_agent"));
        assertTrue(result.sharedContext().containsKey(CollaborationManager.KEY_COLLABORATION_SUMMARY));
        assertTrue(result.executionResult().response().getExecutionMetadata().containsKey("integrationSummary"));
    }

    @Test
    void decidePrefersExplicitProfileTextMatch() {
        AgentProfileService profileService = mock(AgentProfileService.class);
        AgentProfile reportProfile = profile("合同报告", AgentType.REPORT);
        reportProfile.setDescription("合同总结");
        when(profileService.listEnabledProfiles()).thenReturn(List.of(reportProfile));
        OrchestratorAgent orchestrator = newOrchestrator(profileService, mock(ReActAgent.class));
        AnalysisRequest request = request("请用合同总结专家");

        OrchestratorDecision decision = orchestrator.decide(request, null, null);

        assertEquals(AgentType.REPORT, decision.selectedType());
        assertEquals("合同报告", decision.selectedProfile().getName());
    }

    @Test
    void executeStructuredRunsReportPlanWithSharedContext() {
        AgentProfileService profileService = mock(AgentProfileService.class);
        ReActAgent reActAgent = mock(ReActAgent.class);
        AgentProfile reportProfile = profile("报告专家", AgentType.REPORT);
        when(profileService.listEnabledProfiles()).thenReturn(List.of(reportProfile));
        when(reActAgent.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AnalysisResponse.ok("知识依据"));
        AtomicReference<AgentExecutionRequest> capturedReportRequest = new AtomicReference<>();
        AgentSpecialist reportSpecialist = capturingSpecialist(AgentType.REPORT, "最终报告", capturedReportRequest);
        AgentSpecialist knowledgeSpecialist = specialist(AgentType.KNOWLEDGE, "知识依据");
        OrchestratorAgent orchestrator = newOrchestrator(profileService, reActAgent, reportSpecialist,
                knowledgeSpecialist);
        AnalysisRequest request = request("生成销售分析报告");

        OrchestratorResult result = orchestrator.executeStructured(request, null, null);

        assertTrue(result.success());
        assertEquals("最终报告", result.finalAnswer());
        assertEquals("orchestrator:multi-agent", result.executionResult().response().getSkillUsed());
        assertEquals(4, result.executionResult().taskResults().size());
        assertTrue(result.sharedContext().containsKey("taskResults"));
        Map<?, ?> dependencies = (Map<?, ?>) capturedReportRequest.get().specialistTask().sharedContext().get(
                CollaborationManager.KEY_DEPENDENCIES);
        assertTrue(dependencies.containsKey("t3"));
        assertEquals("张三", capturedReportRequest.get().memoryContext().userProfile().displayName());
        verify(reActAgent).execute(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.contains("收集数据源"));
    }

    @Test
    void executeStructuredSkipsDependentTasksWhenUpstreamFails() {
        AgentProfileService profileService = mock(AgentProfileService.class);
        ReActAgent reActAgent = mock(ReActAgent.class);
        AgentProfile dataProfile = profile("数据专家", AgentType.DATA);
        AgentProfile reportProfile = profile("报告专家", AgentType.REPORT);
        when(profileService.listEnabledProfiles()).thenReturn(List.of(dataProfile, reportProfile));
        when(reActAgent.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AnalysisResponse.ok("知识依据"));
        AgentSpecialist failingDataSpecialist = new AgentSpecialist() {
            @Override
            public AgentType type() {
                return AgentType.DATA;
            }

            @Override
            public AnalysisResponse execute(AgentExecutionRequest request) {
                return AnalysisResponse.fail("数据源不可用");
            }
        };
        AtomicReference<AgentExecutionRequest> capturedReportRequest = new AtomicReference<>();
        AgentSpecialist reportSpecialist = capturingSpecialist(AgentType.REPORT, "最终报告", capturedReportRequest);
        OrchestratorAgent orchestrator = newOrchestrator(profileService, reActAgent, failingDataSpecialist,
                reportSpecialist);
        AnalysisRequest request = request("生成销售分析报告");

        OrchestratorResult result = orchestrator.executeStructured(request, null, null);

        assertEquals(4, result.executionResult().taskResults().size());
        assertEquals(false, result.success());
        assertEquals(false, result.executionResult().taskResults().get(0).success());
        assertEquals("SKIPPED", result.executionResult().taskResults().get(2).outputContext().get("status"));
        assertEquals("SKIPPED", result.executionResult().taskResults().get(3).outputContext().get("status"));
        assertEquals("编排器", result.executionResult().taskResults().get(2).specialistId());
        assertEquals("编排器", result.executionResult().taskResults().get(3).specialistId());
        assertEquals("orchestrator:multi-agent", result.executionResult().response().getSkillUsed());
        assertTrue(result.sharedContext().containsKey("skippedTasks"));
        assertEquals(List.of("t3", "t4"), result.sharedContext().get("skippedTasks"));
        assertEquals("上游依赖失败或缺失，跳过任务: t1", result.executionResult().taskResults().get(2).error());
        assertEquals("上游依赖失败或缺失，跳过任务: t3", result.executionResult().taskResults().get(3).error());
        assertEquals(null, capturedReportRequest.get());
    }

    @Test
    void executeStructuredReturnsFriendlyFailureWhenDecisionFails() {
        AgentProfileService profileService = mock(AgentProfileService.class);
        when(profileService.listEnabledProfiles()).thenThrow(new IllegalStateException("租户上下文缺失"));
        OrchestratorAgent orchestrator = newOrchestrator(profileService, mock(ReActAgent.class));
        AnalysisRequest request = request("分析销售趋势");

        OrchestratorResult result = orchestrator.executeStructured(request, null, null);

        assertEquals(false, result.success());
        assertTrue(result.finalAnswer().contains("租户上下文缺失"));
    }

    private OrchestratorAgent newOrchestrator(AgentProfileService profileService,
            ReActAgent reActAgent,
            AgentSpecialist... specialists) {
        List<AgentSpecialist> registeredSpecialists = new ArrayList<>();
        registeredSpecialists.add(specialist(AgentType.REACT, "react-specialist"));
        registeredSpecialists.addAll(List.of(specialists));
        return new OrchestratorAgent(profileService,
                new AgentSpecialistRegistry(registeredSpecialists),
                reActAgent,
                new TaskComplexityClassifier(),
                new IntentAnalyzer(),
                new OrchestratorTaskPlanner(),
                new CollaborationManager(),
                new ResultIntegrator(),
                new ParallelTaskExecutor(Runnable::run),
                memoryManager(),
                new SpecialistFactory(new AgentSpecialistRegistry(registeredSpecialists)));
    }

    private AgentSpecialist specialist(AgentType type, String result) {
        return new AgentSpecialist() {
            @Override
            public AgentType type() {
                return type;
            }

            @Override
            public AnalysisResponse execute(AgentExecutionRequest request) {
                return AnalysisResponse.ok(result);
            }
        };
    }

    private AgentSpecialist capturingSpecialist(AgentType type, String result,
            AtomicReference<AgentExecutionRequest> capturedRequest) {
        return new AgentSpecialist() {
            @Override
            public AgentType type() {
                return type;
            }

            @Override
            public AnalysisResponse execute(AgentExecutionRequest request) {
                capturedRequest.set(request);
                return AnalysisResponse.ok(result);
            }
        };
    }

    private AgentProfile profile(String name, AgentType type) {
        AgentProfile profile = new AgentProfile();
        profile.setName(name);
        profile.setType(type);
        profile.setEnabled(true);
        return profile;
    }

    private AnalysisRequest request(String question) {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion(question);
        return request;
    }

    private MemoryManager memoryManager() {
        MemoryManager memoryManager = mock(MemoryManager.class);
        when(memoryManager.buildContext(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(memoryContext());
        return memoryManager;
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
