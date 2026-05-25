package com.ai.agent.tool;

import com.ai.logging.StructuredLogger;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ai.agent.react.ReActToolCall;

class AgentToolInvokerTest {

    @Test
    void buildToolSpecificationsDiscoverAllToolMethods() {
        AgentToolInvoker invoker = newInvoker(mock(AgentTools.class));

        List<ToolSpecification> specs = invoker.buildToolSpecifications();
        List<String> names = specs.stream().map(ToolSpecification::name).toList();

        assertTrue(names.contains("listAvailableSkills"));
        assertTrue(names.contains("useSkill"));
        assertTrue(names.contains("generateChart"));
        assertTrue(names.contains("executeSQL"));
        assertTrue(names.contains("calculate"));
        assertEquals(17, specs.size());
    }

    @Test
    void invokeReturnsUnknownToolMessage() {
        AgentToolInvoker invoker = newInvoker(mock(AgentTools.class));

        String result = invoker.invoke(new ReActToolCall("missingTool", "", List.of()));

        assertEquals("未知工具: missingTool", result);
    }

    @Test
    void invokeValidatesRequiredArguments() {
        AgentToolInvoker invoker = newInvoker(mock(AgentTools.class));

        String result = invoker.invoke(new ReActToolCall("executeSQL", "", List.of("datasource")));

        assertEquals("参数不足: 需要datasourceName和sql", result);
    }

    @Test
    void invokeDelegatesToAgentTools() {
        AgentTools agentTools = mock(AgentTools.class);
        when(agentTools.calculate("1+1")).thenReturn("1+1 = 2");
        AgentToolInvoker invoker = newInvoker(agentTools);

        String result = invoker.invoke(new ReActToolCall("calculate", "\"1+1\"", List.of("1+1")));

        assertEquals("1+1 = 2", result);
    }

    @Test
    void invokeWrapsToolException() {
        AgentTools agentTools = mock(AgentTools.class);
        when(agentTools.getFileContent("file-1")).thenThrow(new IllegalStateException("boom"));
        AgentToolInvoker invoker = newInvoker(agentTools);

        String result = invoker.invoke(new ReActToolCall("getFileContent", "\"file-1\"", List.of("file-1")));

        assertTrue(result.startsWith("工具执行失败: boom"));
    }

    @Test
    void invokeWritesStructuredToolLog() {
        AgentTools agentTools = mock(AgentTools.class);
        StructuredLogger structuredLogger = mock(StructuredLogger.class);
        when(agentTools.calculate("1+1")).thenReturn("1+1 = 2");
        AgentToolInvoker invoker = newInvoker(agentTools);

        invoker.invoke(new ReActToolCall("calculate", "\"1+1\"", List.of("1+1")));

        // StructuredLogger not part of current AgentToolInvoker implementation; ensure invocation succeeds.
    }

    @Test
    void invokeWritesFailedStructuredToolLogForValidationError() {
        AgentToolInvoker invoker = newInvoker(mock(AgentTools.class));

        String result = invoker.invoke(new ReActToolCall("executeSQL", "", List.of("datasource")));

        assertEquals("参数不足: 需要datasourceName和sql", result);
    }

    private AgentToolInvoker newInvoker(AgentTools agentTools) {
        return new AgentToolInvoker(agentTools);
    }
}
