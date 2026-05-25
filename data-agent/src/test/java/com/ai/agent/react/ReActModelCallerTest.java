package com.ai.agent.react;

import com.ai.mcp.McpModelService;
import com.ai.mcp.TokenMonitor;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReActModelCallerTest {

    @Test
    void callWithToolsReturnsErrorMessageWhenModelThrows() {
        McpModelService modelService = mock(McpModelService.class);
        when(modelService.callModel(anyString(), eq("model-1"))).thenThrow(new IllegalStateException("down"));
        ReActModelCaller caller = new ReActModelCaller(modelService, new ReActPromptBuilder(new TokenMonitor()));

        String result = caller.callWithTools(List.of(UserMessage.from("hello")), List.of(), "model-1");

        assertTrue(result.startsWith("[模型调用失败]"));
        assertTrue(result.contains("down"));
    }

    @Test
    void callStreamingSummaryDelegatesToModelService() {
        McpModelService modelService = mock(McpModelService.class);
        when(modelService.callModelStreaming(anyString(), eq("model-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn("summary");
        ReActModelCaller caller = new ReActModelCaller(modelService, new ReActPromptBuilder(new TokenMonitor()));

        String result = caller.callStreamingSummary(List.of(UserMessage.from("hello")), "model-1", ignored -> {
        });

        assertEquals("summary", result);
    }
}
