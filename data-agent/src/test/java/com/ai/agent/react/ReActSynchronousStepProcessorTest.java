package com.ai.agent.react;

import com.ai.model.AnalysisResponse;
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

class ReActSynchronousStepProcessorTest {

    @Test
    void processAppendsFinalAnswerAndThinkingSteps() {
        ReActResponseParser responseParser = new ReActResponseParser();
        ReActSynchronousStepProcessor processor = newProcessor(responseParser, mock(AgentToolInvoker.class),
                mock(ReActModelCaller.class));
        StringBuilder finalAnswer = new StringBuilder();
        List<AnalysisResponse.ThinkingStep> steps = new ArrayList<>();

        ReActLoopStepResult result = processor.process("[思考] 完成\n最终答案", List.of(), null,
                new ArrayList<>(), finalAnswer, steps, 1, new ReActRecoveryTracker());

        assertFalse(result.shouldContinue());
        assertEquals("最终答案", finalAnswer.toString());
        assertEquals("thinking", steps.get(0).getType());
        assertEquals("answer", steps.get(1).getType());
    }

    @Test
    void processSummarizesWhenToolResultNeedsFinalAnswer() {
        ReActResponseParser responseParser = new ReActResponseParser();
        AgentToolInvoker toolInvoker = mock(AgentToolInvoker.class);
        ReActModelCaller modelCaller = mock(ReActModelCaller.class);
        ReActToolCall toolCall = new ReActToolCall("askUserForInfo", "[\"补充数据\"]", List.of("补充数据"));
        when(toolInvoker.invoke(toolCall)).thenReturn("需要用户输入: 补充数据");
        when(modelCaller.callWithTools(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.isNull())).thenReturn("请补充数据");
        ReActSynchronousStepProcessor processor = newProcessor(responseParser, toolInvoker, modelCaller);
        StringBuilder finalAnswer = new StringBuilder();
        List<AnalysisResponse.ThinkingStep> steps = new ArrayList<>();
        List<ChatMessage> messages = new ArrayList<>();

        ReActLoopStepResult result = processor.process("{\"tool\":\"askUserForInfo\",\"arguments\":[\"补充数据\"]}",
                List.of(), null,
                messages, finalAnswer, steps, 1, new ReActRecoveryTracker());

        assertFalse(result.shouldContinue());
        assertEquals("请补充数据", finalAnswer.toString());
        assertTrue(steps.stream().anyMatch(step -> "tool_call".equals(step.getType())));
        assertTrue(steps.stream().anyMatch(step -> "answer".equals(step.getType())));
    }

    @Test
    void processStopsRecoveryWhenAttemptBudgetIsExhausted() {
        ReActResponseParser responseParser = new ReActResponseParser();
        AgentToolInvoker toolInvoker = mock(AgentToolInvoker.class);
        ReActModelCaller modelCaller = mock(ReActModelCaller.class);
        ReActToolCall toolCall = new ReActToolCall("executeSQL", "[\"ds\",\"select * from missing\"]",
                List.of("ds", "select * from missing"));
        when(toolInvoker.invoke(toolCall)).thenReturn("工具执行失败: SQL syntax error");
        when(modelCaller.callWithTools(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.isNull())).thenReturn("已降级回答");
        ReActSynchronousStepProcessor processor = newProcessor(responseParser, toolInvoker, modelCaller);
        ReActRecoveryTracker tracker = new ReActRecoveryTracker();
        StringBuilder finalAnswer = new StringBuilder();
        List<AnalysisResponse.ThinkingStep> steps = new ArrayList<>();

        processor.process("{\"tool\":\"executeSQL\",\"arguments\":[\"ds\",\"select * from missing\"]}", List.of(), null,
                new ArrayList<>(), new StringBuilder(), new ArrayList<>(), 1, tracker);
        processor.process("{\"tool\":\"executeSQL\",\"arguments\":[\"ds\",\"select * from missing\"]}", List.of(), null,
                new ArrayList<>(), new StringBuilder(), new ArrayList<>(), 2, tracker);
        ReActLoopStepResult result = processor.process("{\"tool\":\"executeSQL\",\"arguments\":[\"ds\",\"select * from missing\"]}",
                List.of(), null, new ArrayList<>(), finalAnswer, steps, 3, tracker);

        assertFalse(result.shouldContinue());
        assertEquals("已降级回答", finalAnswer.toString());
        assertTrue(steps.stream().anyMatch(step -> step.getContent().contains("达到上限")));
    }

    private ReActSynchronousStepProcessor newProcessor(ReActResponseParser responseParser,
            AgentToolInvoker toolInvoker,
            ReActModelCaller modelCaller) {
        return new ReActSynchronousStepProcessor(modelCaller, responseParser,
                new ReActStepHandler(responseParser, toolInvoker, new ErrorRecoveryAdvisor(new AgentReasoningProperties())), new SelfReflector(new AgentReasoningProperties()));
    }
}
