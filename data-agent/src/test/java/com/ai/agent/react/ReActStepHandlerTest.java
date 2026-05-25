package com.ai.agent.react;

import dev.langchain4j.data.message.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.ai.agent.AgentReasoningProperties;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.ToolResult;

class ReActStepHandlerTest {

    @Test
    void handleReturnsFinalAnswerWhenNoToolCallExists() {
        ReActResponseParser responseParser = new ReActResponseParser();
        AgentToolInvoker toolInvoker = mock(AgentToolInvoker.class);
        ReActStepHandler handler = new ReActStepHandler(responseParser, toolInvoker, new ErrorRecoveryAdvisor(new AgentReasoningProperties()));
        List<ChatMessage> messages = new ArrayList<>();

        ReActStepOutcome outcome = handler.handle("[思考] 已完成\n最终答案", messages);

        assertTrue(outcome.isFinalAnswer());
        assertEquals("最终答案", outcome.answer());
        assertTrue(messages.isEmpty());
    }

    @Test
    void handleAppendsToolObservationWhenToolCallExists() {
        ReActResponseParser responseParser = new ReActResponseParser();
        AgentToolInvoker toolInvoker = mock(AgentToolInvoker.class);
        ReActToolCall toolCall = new ReActToolCall("calculate", "[\"1+1\"]", List.of("1+1"));
        when(toolInvoker.invoke(toolCall)).thenReturn("2");
        ReActStepHandler handler = new ReActStepHandler(responseParser, toolInvoker, new ErrorRecoveryAdvisor(new AgentReasoningProperties()));
        List<ChatMessage> messages = new ArrayList<>();

        ReActStepOutcome outcome = handler.handle("""
                [思考] 计算
                {"tool":"calculate","arguments":["1+1"]}
                """, messages);

        assertFalse(outcome.isFinalAnswer());
        assertEquals("calculate", outcome.toolName());
        assertEquals("2", outcome.toolResult());
        assertEquals(2, messages.size());
    }

    @Test
    void handleReturnsMemoryIndexedWhenToolReportsMemoryMarker() {
        ReActResponseParser responseParser = new ReActResponseParser();
        AgentToolInvoker toolInvoker = mock(AgentToolInvoker.class);
        ReActToolCall toolCall = new ReActToolCall("searchMemory", "[\"保存\"]", List.of("保存"));
        when(toolInvoker.invoke(toolCall)).thenReturn("__MEMORY_INDEXED__");
        ReActStepHandler handler = new ReActStepHandler(responseParser, toolInvoker, new ErrorRecoveryAdvisor(new AgentReasoningProperties()));
        List<ChatMessage> messages = new ArrayList<>();

        ReActStepOutcome outcome = handler.handle("""
                {"tool":"searchMemory","arguments":["保存"]}
                """, messages);

        assertTrue(outcome.isMemoryIndexed());
        assertEquals(2, messages.size());
    }

    @Test
    void handleAppendsRecoveryAdviceForToolFailure() {
        ReActResponseParser responseParser = new ReActResponseParser();
        AgentToolInvoker toolInvoker = mock(AgentToolInvoker.class);
        ReActToolCall toolCall = new ReActToolCall("executeSQL", "[\"ds\",\"select * from missing\"]",
                List.of("ds", "select * from missing"));
        when(toolInvoker.invoke(toolCall)).thenReturn("工具执行失败: SQL 表不存在");
        ReActStepHandler handler = new ReActStepHandler(responseParser, toolInvoker, new ErrorRecoveryAdvisor(new AgentReasoningProperties()));
        List<ChatMessage> messages = new ArrayList<>();

        ReActStepOutcome outcome = handler.handle("""
                {"tool":"executeSQL","arguments":["ds","select * from missing"]}
                """, messages);

        assertFalse(outcome.isFinalAnswer());
        assertEquals("工具执行失败: SQL 表不存在", outcome.toolResult());
        assertTrue(outcome.recoveryRequired());
        assertTrue(messages.get(1).toString().contains("错误恢复建议"));
    }
}
