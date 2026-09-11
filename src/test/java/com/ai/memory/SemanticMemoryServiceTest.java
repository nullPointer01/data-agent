package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticMemoryServiceTest {

    @Test
    void shouldDeduplicateBatchByTypeAndStableKeyAndRefreshOnce() {
        LongTermMemory longTermMemory = mock(LongTermMemory.class);
        UserProfileMemoryRefreshService refreshService = mock(UserProfileMemoryRefreshService.class);
        when(longTermMemory.upsertSemantic(eq("tenant"), eq("user"), any(MemoryCaptureRequest.class)))
                .thenReturn(new MemoryEntry());
        SemanticMemoryService service = new SemanticMemoryService(longTermMemory, refreshService);
        SemanticMemoryCandidate first = new SemanticMemoryCandidate(
                MemoryType.PREFERENCE, SemanticMemoryKey.OUTPUT_FORMAT,
                "表格优先", 0.9D, "表格", false);
        SemanticMemoryCandidate replacement = new SemanticMemoryCandidate(
                MemoryType.PREFERENCE, SemanticMemoryKey.OUTPUT_FORMAT,
                "列表优先", 1D, "列表", true);
        SemanticMemoryCandidate invalid = new SemanticMemoryCandidate(
                MemoryType.ENTITY, "manual.invalid", "无效", 1D, "无效", true);

        int affected = service.upsertAll(
                "tenant", "user", "session", List.of(first, replacement, invalid));

        ArgumentCaptor<MemoryCaptureRequest> requestCaptor = ArgumentCaptor.forClass(MemoryCaptureRequest.class);
        verify(longTermMemory, times(1)).upsertSemantic(eq("tenant"), eq("user"), requestCaptor.capture());
        verify(refreshService).refresh("tenant", "user");
        assertThat(affected).isEqualTo(1);
        assertThat(requestCaptor.getValue().effectiveContent()).isEqualTo("列表优先");
        assertThat(requestCaptor.getValue().source()).isEqualTo(MemorySource.USER_EXPLICIT);
    }
}
