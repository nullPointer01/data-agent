package com.ai.agent.react;

import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.memory.MemoryManager;
import com.ai.memory.MemorySource;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;
import com.ai.memory.dto.MemoryContext;
import com.ai.memory.dto.MemoryEntrySummary;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.rag.RagRetrievalService;
import com.ai.rag.dto.RagContextResponse;
import com.ai.service.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ai.agent.tool.AgentConversationRecorder;
import com.ai.agent.orchestrator.ParallelPlanExecutor;
import com.ai.agent.AgentReasoningProperties;
import com.ai.agent.orchestrator.ParallelPlanExecutionResult;
import com.ai.agent.orchestrator.ParallelPlanStepResult;
import com.ai.agent.orchestrator.TaskPlanner;
import com.ai.agent.orchestrator.ExecutionPlan;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.TaskComplexityClassifier;

class ReActAgentFastPathTest {

    @Test
    void executeUsesFastPathForSimpleQuestion() {
        ReActFastAnswerService fastAnswerService = mock(ReActFastAnswerService.class);
        ReActLoopRunner loopRunner = mock(ReActLoopRunner.class);
        AgentConversationRecorder recorder = mock(AgentConversationRecorder.class);
        ParallelPlanExecutor parallelPlanExecutor = mock(ParallelPlanExecutor.class);
        when(parallelPlanExecutor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ParallelPlanExecutionResult(List.of()));
        ReActAgent agent = newAgent(loopRunner, recorder, fastAnswerService, parallelPlanExecutor);
        AnalysisRequest request = request("你好");
        when(fastAnswerService.answer(org.mockito.ArgumentMatchers.eq("你好"), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn("你好，我是 Data Agent");

        AnalysisResponse response = agent.execute(request, null);

        assertEquals("fast-answer", response.getSkillUsed());
        assertEquals("你好，我是 Data Agent", response.getResult());
        assertEquals("fast_path", response.getExecutionMetadata().get("mode"));
        assertEquals(0, response.getExecutionMetadata().get("iterations"));
        assertTrue(response.getExecutionMetadata().containsKey("memoryContext"));
        verify(recorder).recordSessionConversation(null, request, "你好，我是 Data Agent", "fast-answer", null);
        verify(loopRunner, never()).run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void executeKeepsReActForAnalysisQuestion() {
        ReActFastAnswerService fastAnswerService = mock(ReActFastAnswerService.class);
        ReActLoopRunner loopRunner = mock(ReActLoopRunner.class);
        AgentConversationRecorder recorder = mock(AgentConversationRecorder.class);
        ParallelPlanExecutor parallelPlanExecutor = mock(ParallelPlanExecutor.class);
        when(parallelPlanExecutor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ParallelPlanExecutionResult(List.of()));
        ReActAgent agent = newAgent(loopRunner, recorder, fastAnswerService, parallelPlanExecutor);
        AnalysisRequest request = request("分析销售趋势");
        when(loopRunner.run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new ReActExecutionResult("分析结果", 1));

        AnalysisResponse response = agent.execute(request, null);

        assertEquals("react-agent", response.getSkillUsed());
        assertEquals("分析结果", response.getResult());
        assertEquals("reasoning", response.getExecutionMetadata().get("mode"));
        assertEquals(1, response.getExecutionMetadata().get("iterations"));
        assertTrue(response.getExecutionMetadata().containsKey("executionPlan"));
        assertTrue(response.getExecutionMetadata().containsKey("parallelPrecheck"));
        verify(fastAnswerService, never()).answer(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void executeUsesReasoningWhenFastPathIsDisabled() {
        ReActFastAnswerService fastAnswerService = mock(ReActFastAnswerService.class);
        ReActLoopRunner loopRunner = mock(ReActLoopRunner.class);
        AgentConversationRecorder recorder = mock(AgentConversationRecorder.class);
        ParallelPlanExecutor parallelPlanExecutor = mock(ParallelPlanExecutor.class);
        when(parallelPlanExecutor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ParallelPlanExecutionResult(List.of()));
        AgentReasoningProperties properties = new AgentReasoningProperties();
        properties.setFastPathEnabled(false);
        ReActAgent agent = newAgent(loopRunner, recorder, fastAnswerService, parallelPlanExecutor, properties);
        AnalysisRequest request = request("你好");
        when(loopRunner.run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new ReActExecutionResult("推理回答", 1));

        AnalysisResponse response = agent.execute(request, null);

        assertEquals("react-agent", response.getSkillUsed());
        assertEquals("推理回答", response.getResult());
        verify(fastAnswerService, never()).answer(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void executeUsesPassthroughPlanWhenPlanningIsDisabled() {
        ReActFastAnswerService fastAnswerService = mock(ReActFastAnswerService.class);
        ReActLoopRunner loopRunner = mock(ReActLoopRunner.class);
        AgentConversationRecorder recorder = mock(AgentConversationRecorder.class);
        ParallelPlanExecutor parallelPlanExecutor = mock(ParallelPlanExecutor.class);
        when(parallelPlanExecutor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ParallelPlanExecutionResult(List.of()));
        AgentReasoningProperties properties = new AgentReasoningProperties();
        properties.setPlanningEnabled(false);
        ReActAgent agent = newAgent(loopRunner, recorder, fastAnswerService, parallelPlanExecutor, properties);
        AnalysisRequest request = request("分析销售趋势");
        when(loopRunner.run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new ReActExecutionResult("分析结果", 1));

        AnalysisResponse response = agent.execute(request, null);

        ExecutionPlan plan = (ExecutionPlan) response.getExecutionMetadata().get("executionPlan");
        assertEquals("react-loop", plan.steps().get(0).id());
        assertTrue(response.getExecutionMetadata().containsKey("reasoningFeatures"));
    }

    @Test
    void executeAddsParallelPrecheckThinkingStepWhenAvailable() {
        ReActFastAnswerService fastAnswerService = mock(ReActFastAnswerService.class);
        ReActLoopRunner loopRunner = mock(ReActLoopRunner.class);
        AgentConversationRecorder recorder = mock(AgentConversationRecorder.class);
        ParallelPlanExecutor parallelPlanExecutor = mock(ParallelPlanExecutor.class);
        when(parallelPlanExecutor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ParallelPlanExecutionResult(List.of(
                        new ParallelPlanStepResult("retrieve-knowledge", "searchKnowledge", true, "知识结果"))));
        ReActAgent agent = newAgent(loopRunner, recorder, fastAnswerService, parallelPlanExecutor);
        AnalysisRequest request = request("分析销售趋势");
        when(loopRunner.run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new ReActExecutionResult("分析结果", 1));

        AnalysisResponse response = agent.execute(request, null);

        assertTrue(response.getThinkingSteps().stream()
                .anyMatch(step -> "parallel_precheck".equals(step.getType())));
        assertTrue(response.getThinkingSteps().stream()
                .anyMatch(step -> step.getContent().contains("知识结果")));
    }


    @Test
    void executeStreamingUsesFastPathForSimpleQuestion() {
        ReActFastAnswerService fastAnswerService = mock(ReActFastAnswerService.class);
        ReActLoopRunner loopRunner = mock(ReActLoopRunner.class);
        AgentConversationRecorder recorder = mock(AgentConversationRecorder.class);
        ParallelPlanExecutor parallelPlanExecutor = mock(ParallelPlanExecutor.class);
        when(parallelPlanExecutor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ParallelPlanExecutionResult(List.of()));
        ReActAgent agent = newAgent(loopRunner, recorder, fastAnswerService, parallelPlanExecutor);
        AnalysisRequest request = request("你好");
        when(fastAnswerService.answerStreaming(org.mockito.ArgumentMatchers.eq("你好"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
                    invocation.<java.util.function.Consumer<String>>getArgument(3).accept("你好");
                    return "你好";
                });
        List<String> events = new ArrayList<>();

        agent.executeStreaming(request, null, events::add);

        assertTrue(events.stream().anyMatch(event -> event.contains("\"type\":\"token\"")));
        assertTrue(events.stream().anyMatch(event -> event.contains("\"type\":\"done\"")));
        verify(loopRunner, never()).runStreaming(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void executeStreamingEmitsPlanAndParallelPrecheckForReasoningQuestion() {
        ReActFastAnswerService fastAnswerService = mock(ReActFastAnswerService.class);
        ReActLoopRunner loopRunner = mock(ReActLoopRunner.class);
        AgentConversationRecorder recorder = mock(AgentConversationRecorder.class);
        ParallelPlanExecutor parallelPlanExecutor = mock(ParallelPlanExecutor.class);
        when(parallelPlanExecutor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ParallelPlanExecutionResult(List.of(
                        new ParallelPlanStepResult("retrieve-knowledge", "searchKnowledge", true, "知识结果"))));
        when(loopRunner.runStreaming(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("分析结果");
        ReActAgent agent = newAgent(loopRunner, recorder, fastAnswerService, parallelPlanExecutor);
        AnalysisRequest request = request("分析销售趋势");
        List<String> events = new ArrayList<>();

        agent.executeStreaming(request, null, events::add);

        assertTrue(events.stream().anyMatch(event -> event.contains("\"type\":\"execution_plan\"")));
        assertTrue(events.stream().anyMatch(event -> event.contains("\"type\":\"parallel_precheck\"")));
        assertTrue(events.stream().anyMatch(event -> event.contains("知识结果")));
    }

    private ReActAgent newAgent(ReActLoopRunner loopRunner,
            AgentConversationRecorder recorder,
            ReActFastAnswerService fastAnswerService,
            ParallelPlanExecutor parallelPlanExecutor) {
        return newAgent(loopRunner, recorder, fastAnswerService, parallelPlanExecutor, new AgentReasoningProperties());
    }

    private ReActAgent newAgent(ReActLoopRunner loopRunner,
            AgentConversationRecorder recorder,
            ReActFastAnswerService fastAnswerService,
            ParallelPlanExecutor parallelPlanExecutor,
            AgentReasoningProperties reasoningProperties) {
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        when(ragRetrievalService.retrieve(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(RagContextResponse.empty());
        MemoryManager memoryManager = mock(MemoryManager.class);
        when(memoryManager.buildContext(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(memoryContext());
        return new ReActAgent(
                mock(SessionManager.class),
                mock(AgentToolInvoker.class),
                new ReActRequestContextBuilder(ragRetrievalService, null),
                recorder,
                new ReActStreamEventWriter(new ObjectMapper()),
                loopRunner,
                new ReActFastPathDecider(new TaskComplexityClassifier()),
                fastAnswerService,
                new TaskPlanner(),
                parallelPlanExecutor,
                reasoningProperties,
                memoryManager,
                new ReActMetadataBuilder(reasoningProperties, new MemoryContextPromptFormatter()));
    }

    private AnalysisRequest request(String question) {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion(question);
        return request;
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
