package com.ai.service;

import com.ai.agent.DataAnalysisAgent;
import com.ai.agent.react.ReActStreamEventWriter;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisStreamServiceTest {

    @Test
    void streamDelegatesToUnifiedAnalysisAgent() {
        DataAnalysisAgent dataAnalysisAgent = mock(DataAnalysisAgent.class);
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("分析销售趋势");
        when(dataAnalysisAgent.analyze(request)).thenReturn(AnalysisResponse.ok("ok"));
        Executor directExecutor = Runnable::run;
        AnalysisStreamService service = new AnalysisStreamService(dataAnalysisAgent,
                new ReActStreamEventWriter(new ObjectMapper()), new ObjectMapper(), directExecutor, 600_000L);

        service.stream(request);

        verify(dataAnalysisAgent).analyze(request);
    }

    @Test
    void streamEmitsOrchestrationEventsForThinkingSteps() {
        CapturingEventWriter writer = new CapturingEventWriter(new ObjectMapper());
        DataAnalysisAgent dataAnalysisAgent = mock(DataAnalysisAgent.class);
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("分析销售趋势");
        AnalysisResponse response = AnalysisResponse.ok("ok");
        response.setThinkingSteps(List.of(new AnalysisResponse.ThinkingStep(0, "orchestrator", "选择数据专家")));
        when(dataAnalysisAgent.analyze(request)).thenReturn(response);
        AnalysisStreamService service = new AnalysisStreamService(dataAnalysisAgent,
                writer, new ObjectMapper(), Runnable::run, 600_000L);

        service.stream(request);

        Assertions.assertEquals("编排决策", writer.title);
        Assertions.assertEquals("选择数据专家", writer.content);
    }

    @Test
    void streamEmitsReasoningPreparationEventsForThinkingSteps() {
        CapturingEventWriter writer = new CapturingEventWriter(new ObjectMapper());
        DataAnalysisAgent dataAnalysisAgent = mock(DataAnalysisAgent.class);
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("分析销售趋势");
        AnalysisResponse response = AnalysisResponse.ok("ok");
        response.setThinkingSteps(List.of(
                new AnalysisResponse.ThinkingStep(0, "plan", "阶段 1: 检索知识库"),
                new AnalysisResponse.ThinkingStep(0, "parallel_precheck", "知识结果")));
        when(dataAnalysisAgent.analyze(request)).thenReturn(response);
        AnalysisStreamService service = new AnalysisStreamService(dataAnalysisAgent,
                writer, new ObjectMapper(), Runnable::run, 600_000L);

        service.stream(request);

        Assertions.assertEquals("阶段 1: 检索知识库", writer.executionPlanContent);
        Assertions.assertEquals("知识结果", writer.parallelPrecheckContent);
    }

    private static final class CapturingEventWriter extends ReActStreamEventWriter {

        private String title;
        private String content;
        private String executionPlanContent;
        private String parallelPrecheckContent;

        private CapturingEventWriter(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public void emitOrchestration(java.util.function.Consumer<String> emitter, String title, String content) {
            this.title = title;
            this.content = content;
            super.emitOrchestration(emitter, title, content);
        }

        @Override
        public void emitExecutionPlan(java.util.function.Consumer<String> emitter, String content) {
            this.executionPlanContent = content;
            super.emitExecutionPlan(emitter, content);
        }

        @Override
        public void emitParallelPrecheck(java.util.function.Consumer<String> emitter, String content) {
            this.parallelPrecheckContent = content;
            super.emitParallelPrecheck(emitter, content);
        }
    }
}
