package com.ai.memory;

import com.ai.repository.MemoryEntryRepository;
import com.ai.service.VectorMemoryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryQuotaServiceTest {

    @Test
    void pruneBeforeCaptureDeletesLowValueMemoriesWhenQuotaReached() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        MemoryProperties properties = new MemoryProperties();
        properties.setMaxMemoriesPerUser(2);
        MemoryEntry lowValue = memory("m-1", MemoryTier.LONG_TERM);
        when(repository.countByTenantIdAndUserId("tenant-1", "user-1")).thenReturn(2L);
        when(repository.findPrunableMemories(eq("tenant-1"), eq("user-1"), any(Pageable.class)))
                .thenReturn(List.of(lowValue));
        MemoryQuotaService service = new MemoryQuotaService(repository, vectorMemoryService, properties,
                new SimpleMeterRegistry());

        int prunedCount = service.pruneBeforeCapture("tenant-1", "user-1");

        assertEquals(1, prunedCount);
        verify(vectorMemoryService).removeFromStore("memory", "m-1", "tenant-1");
        verify(repository).deleteAll(List.of(lowValue));
    }

    @Test
    void pruneBeforeCaptureSkipsWhenQuotaAvailable() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        MemoryProperties properties = new MemoryProperties();
        properties.setMaxMemoriesPerUser(2);
        when(repository.countByTenantIdAndUserId("tenant-1", "user-1")).thenReturn(1L);
        MemoryQuotaService service = new MemoryQuotaService(repository, mock(VectorMemoryService.class), properties,
                new SimpleMeterRegistry());

        int prunedCount = service.pruneBeforeCapture("tenant-1", "user-1");

        assertEquals(0, prunedCount);
        verify(repository, never()).findPrunableMemories(eq("tenant-1"), eq("user-1"), any(Pageable.class));
    }

    private MemoryEntry memory(String memoryId, MemoryTier tier) {
        MemoryEntry entry = new MemoryEntry();
        entry.setMemoryId(memoryId);
        entry.setTenantId("tenant-1");
        entry.setUserId("user-1");
        entry.setTier(tier);
        return entry;
    }
}
