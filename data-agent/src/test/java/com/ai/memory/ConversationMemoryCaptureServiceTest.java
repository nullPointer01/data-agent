package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ConversationMemoryCaptureServiceTest {

    @Test
    void captureCompletedConversationStoresShortTermSummary() {
        MemoryManager memoryManager = mock(MemoryManager.class);
        ConversationMemoryCaptureService service = service(memoryManager);
        ArgumentCaptor<MemoryCaptureRequest> captor = ArgumentCaptor.forClass(MemoryCaptureRequest.class);
        String assistantReply = "销售额同比增长 12%。".repeat(40);

        service.captureCompletedConversation("session-1", "分析一下销售额", assistantReply);

        verify(memoryManager).capture(captor.capture());
        MemoryCaptureRequest request = captor.getValue();
        assertEquals(MemoryTier.SHORT_TERM, request.tier());
        assertEquals(MemoryType.SUMMARY, request.type());
        assertEquals(MemorySource.SYSTEM_GENERATED, request.source());
        assertEquals("session-1", request.sessionId());
        assertTrue(request.content().contains("用户: 分析一下销售额"));
        assertTrue(request.compressedContent().contains("销售额同比增长 12%"));
        assertTrue(request.compressedContent().length() < request.content().length());
        assertEquals(List.of("conversation"), request.topicTags());
    }

    @Test
    void captureCompletedConversationStoresExplicitPreferenceAsLongTermMemory() {
        MemoryManager memoryManager = mock(MemoryManager.class);
        ConversationMemoryCaptureService service = service(memoryManager);
        ArgumentCaptor<MemoryCaptureRequest> captor = ArgumentCaptor.forClass(MemoryCaptureRequest.class);

        service.captureCompletedConversation("session-1", "请记住，我偏好用表格展示分析结果", "好的");

        verify(memoryManager, times(2)).capture(captor.capture());
        MemoryCaptureRequest request = captor.getAllValues().get(1);
        assertEquals(MemoryTier.LONG_TERM, request.tier());
        assertEquals(MemoryType.PREFERENCE, request.type());
        assertEquals(MemorySource.USER_EXPLICIT, request.source());
        assertEquals(5, request.importance());
        assertTrue(request.compressedContent().contains("用户明确要求记住"));
        assertEquals(List.of("user_memory", "preference"), request.topicTags());
    }

    @Test
    void captureCompletedConversationSkipsBlankTurns() {
        MemoryManager memoryManager = mock(MemoryManager.class);
        ConversationMemoryCaptureService service = service(memoryManager);

        service.captureCompletedConversation("session-1", " ", null);

        verify(memoryManager, never()).capture(org.mockito.ArgumentMatchers.any());
    }

    private ConversationMemoryCaptureService service(MemoryManager memoryManager) {
        return new ConversationMemoryCaptureService(
                memoryManager,
                new DeterministicMemoryCompressor(),
                new MemoryWorthinessEvaluator());
    }
}
