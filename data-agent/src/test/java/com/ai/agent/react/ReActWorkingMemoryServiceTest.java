package com.ai.agent.react;

import com.ai.memory.MemoryManager;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.ai.agent.AgentReasoningProperties;

class ReActWorkingMemoryServiceTest {

    @Test
    void recordStartSavesSessionScopedState() {
        MemoryManager memoryManager = mock(MemoryManager.class);
        ReActWorkingMemoryService service = new ReActWorkingMemoryService(memoryManager, new AgentReasoningProperties());

        service.recordStart("session-1", "分析销售数据");

        verify(memoryManager).saveWorkingMemory(eq("session-1"), contains("执行状态: STARTED"));
    }

    @Test
    void recordIterationStoresLatestModelOutput() {
        MemoryManager memoryManager = mock(MemoryManager.class);
        ReActWorkingMemoryService service = new ReActWorkingMemoryService(memoryManager, new AgentReasoningProperties());

        service.recordIteration("session-1", "问题", 2, "模型输出",
                new ReActLoopStepResult(true, false), "部分答案");

        verify(memoryManager).saveWorkingMemory(eq("session-1"), contains("最近模型输出: 模型输出"));
        verify(memoryManager).saveWorkingMemory(eq("session-1"), contains("当前答案: 部分答案"));
    }

    @Test
    void recordStartSkipsBlankSession() {
        MemoryManager memoryManager = mock(MemoryManager.class);
        ReActWorkingMemoryService service = new ReActWorkingMemoryService(memoryManager, new AgentReasoningProperties());

        service.recordStart(null, "分析销售数据");

        verify(memoryManager, never()).saveWorkingMemory(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void recordCompletionDoesNotBreakWhenMemoryLayerFails() {
        MemoryManager memoryManager = mock(MemoryManager.class);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(memoryManager).saveWorkingMemory(eq("session-1"), org.mockito.ArgumentMatchers.anyString());
        ReActWorkingMemoryService service = new ReActWorkingMemoryService(memoryManager, new AgentReasoningProperties());

        service.recordCompletion("session-1", "问题", 1, "答案");

        verify(memoryManager).saveWorkingMemory(eq("session-1"), contains("执行状态: COMPLETED"));
    }
}
