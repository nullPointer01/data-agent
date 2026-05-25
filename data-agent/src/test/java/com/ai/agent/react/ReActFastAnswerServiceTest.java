package com.ai.agent.react;

import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.memory.MemorySource;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;
import com.ai.memory.dto.MemoryContext;
import com.ai.memory.dto.MemoryEntrySummary;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.mcp.McpModelService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReActFastAnswerServiceTest {

    @Test
    void answerCallsModelWithDirectAnswerPrompt() {
        McpModelService modelService = mock(McpModelService.class);
        when(modelService.callModel(contains("当前请求被判定为简单直答"), eq("model-1"))).thenReturn("你好");
        ReActFastAnswerService service = service(modelService);

        String result = service.answer("你好", "model-1");

        assertEquals("你好", result);
    }

    @Test
    void answerStreamingDelegatesToStreamingModelCall() {
        McpModelService modelService = mock(McpModelService.class);
        when(modelService.callModelStreaming(contains("当前请求被判定为简单直答"), eq("model-1"),
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
                    invocation.<java.util.function.Consumer<String>>getArgument(2).accept("你");
                    invocation.<java.util.function.Consumer<String>>getArgument(2).accept("好");
                    return "你好";
                });
        ReActFastAnswerService service = service(modelService);
        List<String> tokens = new ArrayList<>();

        String result = service.answerStreaming("你好", "model-1", tokens::add);

        assertEquals("你好", result);
        assertEquals(List.of("你", "好"), tokens);
    }

    @Test
    void answerAddsMemoryContextWhenAvailable() {
        McpModelService modelService = mock(McpModelService.class);
        when(modelService.callModel(org.mockito.ArgumentMatchers.argThat(prompt -> prompt.contains("[记忆上下文]")
                && prompt.contains("称呼=张三")
                && prompt.contains("用户关注复购率")), eq("model-1"))).thenReturn("记住了");
        ReActFastAnswerService service = service(modelService);

        String result = service.answer("我是谁", "model-1", memoryContext());

        assertEquals("记住了", result);
    }

    private ReActFastAnswerService service(McpModelService modelService) {
        return new ReActFastAnswerService(modelService, new MemoryContextPromptFormatter());
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
