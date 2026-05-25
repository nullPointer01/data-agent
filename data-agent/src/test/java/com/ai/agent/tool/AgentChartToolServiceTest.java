package com.ai.agent.tool;

import com.ai.mcp.McpModelService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentChartToolServiceTest {

    @Test
    void generateChartStripsMarkdownJsonFence() {
        McpModelService modelService = mock(McpModelService.class);
        when(modelService.callModel(any(String.class), isNull())).thenReturn("""
                ```json
                {"title":{"text":"销售"}}
                ```
                """);
        AgentChartToolService service = new AgentChartToolService(modelService);

        String result = service.generateChart("bar", "[{\"name\":\"A\",\"value\":1}]", "销售");

        assertEquals("CHART:{\"title\":{\"text\":\"销售\"}}", result);
    }

    @Test
    void generateChartReturnsRecoverableErrorWhenModelFails() {
        McpModelService modelService = mock(McpModelService.class);
        when(modelService.callModel(any(String.class), isNull())).thenThrow(new IllegalStateException("down"));
        AgentChartToolService service = new AgentChartToolService(modelService);

        String result = service.generateChart("bar", "[]", "销售");

        assertTrue(result.startsWith("图表生成失败: down"));
    }
}
