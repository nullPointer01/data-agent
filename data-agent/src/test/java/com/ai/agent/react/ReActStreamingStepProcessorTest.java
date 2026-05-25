package com.ai.agent.react;

import com.ai.model.AnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.ToolResult;
import com.ai.agent.AgentReasoningProperties;

class ReActStreamingStepProcessorTest {

    @Test
    void processReturnsFinalAnswerWhenNoToolCallExists() {
        ReActResponseParser responseParser = new ReActResponseParser();
        ReActStreamingStepProcessor processor = newProcessor(responseParser, mock(AgentToolInvoker.class),
                mock(ReActModelCaller.class));
        StringBuilder finalAnswer = new StringBuilder();

        ReActLoopStepResult result = processor.process("最终答案", new ArrayList<>(), List.of(), null,
                finalAnswer, new ArrayList<>(), ignored -> {
                }, new ReActRecoveryTracker());

        assertFalse(result.shouldContinue());
        assertEquals("最终答案", finalAnswer.toString());
    }

    @Test
    void processEmitsToolCallAndContinuesForNormalToolResult() {
        ReActResponseParser responseParser = new ReActResponseParser();
        AgentToolInvoker toolInvoker = mock(AgentToolInvoker.class);
        ReActToolCall toolCall = new ReActToolCall("calculate", "[\"1+1\"]", List.of("1+1"));
        when(toolInvoker.invoke(toolCall)).thenReturn("2");
        ReActStreamingStepProcessor processor = newProcessor(responseParser, toolInvoker, mock(ReActModelCaller.class));
        List<String> events = new ArrayList<>();
        List<ChatMessage> messages = new ArrayList<>();

        ReActLoopStepResult result = processor.process("{\"tool\":\"calculate\",\"arguments\":[\"1+1\"]}", messages,
                List.of(), null,
                new StringBuilder(), new ArrayList<>(), events::add, new ReActRecoveryTracker());

        assertTrue(result.shouldContinue());
        assertTrue(events.stream().anyMatch(event -> event.contains("\"toolName\":\"calculate\"")));
    }

    @Test
    void processStreamsSummaryWhenToolResultNeedsFinalAnswer() {
        ReActResponseParser responseParser = new ReActResponseParser();
        AgentToolInvoker toolInvoker = mock(AgentToolInvoker.class);
        ReActModelCaller modelCaller = mock(ReActModelCaller.class);
        ReActToolCall toolCall = new ReActToolCall("askUserForInfo", "[\"补充数据\"]", List.of("补充数据"));
        when(toolInvoker.invoke(toolCall)).thenReturn("需要用户输入: 补充数据");
        when(modelCaller.callStreamingSummary(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    invocation.<java.util.function.Consumer<String>>getArgument(2).accept("请补充数据");
                    return "请补充数据";
                });
        ReActStreamingStepProcessor processor = newProcessor(responseParser, toolInvoker, modelCaller);
        StringBuilder finalAnswer = new StringBuilder();
        List<String> events = new ArrayList<>();
        List<AnalysisResponse.ThinkingStep> thinkingSteps = new ArrayList<>();

        ReActLoopStepResult result = processor.process("{\"tool\":\"askUserForInfo\",\"arguments\":[\"补充数据\"]}",
                new ArrayList<>(),
                List.of(), null, finalAnswer, thinkingSteps, events::add, new ReActRecoveryTracker());

        assertFalse(result.shouldContinue());
        assertEquals("请补充数据", finalAnswer.toString());
        assertTrue(events.stream().anyMatch(event -> event.contains("\"type\":\"token\"")));
        assertTrue(thinkingSteps.stream().anyMatch(step -> "reflection".equals(step.getType())));
    }

    @Test
    void processEmitsReflectionForRecoverableToolFailure() {
        ReActResponseParser responseParser = new ReActResponseParser();
        AgentToolInvoker toolInvoker = mock(AgentToolInvoker.class);
        ReActToolCall toolCall = new ReActToolCall("executeSQL", "[\"ds\",\"select * from missing\"]",
                List.of("ds", "select * from missing"));
        when(toolInvoker.invoke(toolCall)).thenReturn("工具执行失败: SQL syntax error");
        ReActStreamingStepProcessor processor = newProcessor(responseParser, toolInvoker, mock(ReActModelCaller.class));
        List<String> events = new ArrayList<>();
        List<AnalysisResponse.ThinkingStep> thinkingSteps = new ArrayList<>();

        ReActLoopStepResult result = processor.process("{\"tool\":\"executeSQL\",\"arguments\":[\"ds\",\"select * from missing\"]}",
                new ArrayList<>(), List.of(), null, new StringBuilder(), thinkingSteps, events::add,
                new ReActRecoveryTracker());

        assertTrue(result.shouldContinue());
        assertTrue(result.recoveryRequired());
        assertTrue(events.stream().anyMatch(event -> event.contains("\"type\":\"reflection\"")));
        assertTrue(thinkingSteps.stream().anyMatch(step -> "tool_call".equals(step.getType())));
    }

    private ReActStreamingStepProcessor newProcessor(ReActResponseParser responseParser,
            AgentToolInvoker toolInvoker,
            ReActModelCaller modelCaller) {
        return new ReActStreamingStepProcessor(modelCaller, responseParser,
                new ReActStepHandler(responseParser, toolInvoker, new ErrorRecoveryAdvisor(new AgentReasoningProperties())),
                new ReActStreamEventWriter(new ObjectMapper()), new SelfReflector(new AgentReasoningProperties()));
    }
}
