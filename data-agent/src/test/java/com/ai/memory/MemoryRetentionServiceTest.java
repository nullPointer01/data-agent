package com.ai.memory;

import com.ai.repository.MemoryEntryRepository;
import com.ai.service.VectorMemoryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryRetentionServiceTest {

    @Test
    void cleanupExpiredMemoriesDeletesEntriesAndLongTermVectors() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MemoryRetentionService service = new MemoryRetentionService(repository, vectorMemoryService,
                new MemoryProperties(), meterRegistry);
        MemoryEntry shortTerm = memory("m-1", MemoryTier.SHORT_TERM);
        MemoryEntry longTerm = memory("m-2", MemoryTier.LONG_TERM);
        LocalDateTime now = LocalDateTime.now();
        when(repository.findExpiredMemories(eq(now), any(Pageable.class))).thenReturn(List.of(shortTerm, longTerm));

        int count = service.cleanupExpiredMemories(now, 50);

        assertEquals(2, count);
        verify(vectorMemoryService).removeFromStore("memory", "m-2", "tenant-1");
        verify(repository).deleteAll(List.of(shortTerm, longTerm));
        assertEquals(2D, meterRegistry.counter("data_agent_memory_cleanup_total", "reason", "expired").count());
    }

    @Test
    void cleanupExpiredMemoriesSkipsDeleteWhenNoExpiredEntries() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        MemoryRetentionService service = new MemoryRetentionService(repository, mock(VectorMemoryService.class),
                new MemoryProperties(), new SimpleMeterRegistry());
        LocalDateTime now = LocalDateTime.now();
        when(repository.findExpiredMemories(eq(now), any(Pageable.class))).thenReturn(List.of());

        int count = service.cleanupExpiredMemories(now, 50);

        assertEquals(0, count);
        verify(repository, never()).deleteAll(List.of());
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
