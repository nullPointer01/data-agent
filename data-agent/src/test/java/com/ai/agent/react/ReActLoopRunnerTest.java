package com.ai.agent.react;

import com.ai.logging.StructuredLogger;
import com.ai.mcp.McpModelService;
import com.ai.memory.MemoryManager;
import com.ai.mcp.TokenMonitor;
import com.ai.model.AnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ai.agent.AgentReasoningProperties;
import com.ai.agent.tool.AgentToolInvoker;

class ReActLoopRunnerTest {

    @Test
    void runReturnsFinalAnswer() {
        McpModelService mcpModelService = mock(McpModelService.class);
        ReActResponseParser responseParser = new ReActResponseParser();
        ReActLoopRunner runner = newRunner(mcpModelService, responseParser, newStepHandler(responseParser));
        List<ChatMessage> messages = new ArrayList<>(List.of(UserMessage.from("hello")));
        List<AnalysisResponse.ThinkingStep> steps = new ArrayList<>();
        when(mcpModelService.callModel(anyString(), eq(null))).thenReturn("[思考] 完成\n最终答案");

        ReActExecutionResult result = runner.run(messages, List.of(), null, "session-1", "hello", steps);

        assertEquals("最终答案", result.answer());
        assertEquals(1, result.iterations());
        assertEquals("thinking", steps.get(0).getType());
        assertEquals("answer", steps.get(1).getType());
    }

    @Test
    void runReturnsFailureWhenModelResponseIsEmpty() {
        McpModelService mcpModelService = mock(McpModelService.class);
        ReActResponseParser responseParser = new ReActResponseParser();
        ReActLoopRunner runner = newRunner(mcpModelService, responseParser, newStepHandler(responseParser));
        List<AnalysisResponse.ThinkingStep> steps = new ArrayList<>();
        when(mcpModelService.callModel(anyString(), eq(null))).thenReturn("");

        ReActExecutionResult result = runner.run(new ArrayList<>(), List.of(), null, "session-1", "hello", steps);

        assertEquals("模型调用失败，请稍后重试", result.answer());
        assertEquals("error", steps.get(0).getType());
    }

    @Test
    void runStreamingEmitsTokenAndReturnsAnswer() {
        McpModelService mcpModelService = mock(McpModelService.class);
        ReActResponseParser responseParser = new ReActResponseParser();
        ReActLoopRunner runner = newRunner(mcpModelService, responseParser, newStepHandler(responseParser));
        List<String> events = new ArrayList<>();
        when(mcpModelService.callModelStreaming(anyString(), eq(null), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    invocation.<java.util.function.Consumer<String>>getArgument(2).accept("最终");
                    invocation.<java.util.function.Consumer<String>>getArgument(2).accept("答案");
                    return "最终答案";
                });

        String answer = runner.runStreaming(new ArrayList<>(), List.of(), null, "session-1", "hello", events::add);

        assertEquals("最终答案", answer);
        assertTrue(events.stream().anyMatch(event -> event.contains("\"type\":\"token\"")));
        assertTrue(events.stream().anyMatch(event -> event.contains("\"type\":\"thinking_start\"")));
    }

    @Test
    void runWritesWorkingMemorySnapshots() {
        McpModelService mcpModelService = mock(McpModelService.class);
        ReActResponseParser responseParser = new ReActResponseParser();
        MemoryManager memoryManager = mock(MemoryManager.class);
        ReActLoopRunner runner = newRunner(mcpModelService, responseParser, newStepHandler(responseParser),
                memoryManager);
        when(mcpModelService.callModel(anyString(), eq(null))).thenReturn("最终答案");

        runner.run(new ArrayList<>(), List.of(), null, "session-1", "hello", new ArrayList<>());

        verify(memoryManager, org.mockito.Mockito.atLeastOnce()).saveWorkingMemory(eq("session-1"),
                org.mockito.ArgumentMatchers.contains("执行状态"));
    }

    @Test
    void runWritesStructuredAgentSteps() {
        McpModelService mcpModelService = mock(McpModelService.class);
        ReActResponseParser responseParser = new ReActResponseParser();
        StructuredLogger structuredLogger = mock(StructuredLogger.class);
        ReActLoopRunner runner = newRunner(mcpModelService, responseParser, newStepHandler(responseParser),
                mock(MemoryManager.class), structuredLogger);
        when(mcpModelService.callModel(anyString(), eq(null))).thenReturn("最终答案");

        runner.run(new ArrayList<>(), List.of(), null, "session-1", "hello", new ArrayList<>());

        verify(structuredLogger, atLeastOnce()).logAgentStep(eq("session-1"), eq("REACT_START"),
                org.mockito.ArgumentMatchers.anyMap());
        verify(structuredLogger, atLeastOnce()).logAgentStep(eq("session-1"), eq("REACT_COMPLETION"),
                org.mockito.ArgumentMatchers.anyMap());
    }

    private ReActLoopRunner newRunner(McpModelService mcpModelService,
            ReActResponseParser responseParser,
            ReActStepHandler stepHandler) {
        MemoryManager memoryManager = mock(MemoryManager.class);
        return newRunner(mcpModelService, responseParser, stepHandler, memoryManager, mock(StructuredLogger.class));
    }

    private ReActLoopRunner newRunner(McpModelService mcpModelService,
            ReActResponseParser responseParser,
            ReActStepHandler stepHandler,
            MemoryManager memoryManager) {
        return newRunner(mcpModelService, responseParser, stepHandler, memoryManager, mock(StructuredLogger.class));
    }

    private ReActLoopRunner newRunner(McpModelService mcpModelService,
            ReActResponseParser responseParser,
            ReActStepHandler stepHandler,
            MemoryManager memoryManager,
            StructuredLogger structuredLogger) {
        ReActPromptBuilder promptBuilder = new ReActPromptBuilder(new TokenMonitor());
        ReActModelCaller modelCaller = new ReActModelCaller(mcpModelService, promptBuilder);
        ReActStreamEventWriter streamEventWriter = new ReActStreamEventWriter(new ObjectMapper());
        return new ReActLoopRunner(
                modelCaller,
                responseParser,
                new ReActSynchronousStepProcessor(modelCaller, responseParser, stepHandler, new SelfReflector(new AgentReasoningProperties())),
                new ReActStreamingStepProcessor(modelCaller, responseParser, stepHandler, streamEventWriter,
                        new SelfReflector(new AgentReasoningProperties())),
                streamEventWriter,
                new ReActWorkingMemoryService(memoryManager, new AgentReasoningProperties()),
                structuredLogger);
    }

    private ReActStepHandler newStepHandler(ReActResponseParser responseParser) {
        return new ReActStepHandler(responseParser, mock(AgentToolInvoker.class), new ErrorRecoveryAdvisor(new AgentReasoningProperties()));
    }
}
