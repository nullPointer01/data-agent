package com.ai.memory;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryDecaySchedulerTest {

    @Test
    void maintainMemoriesAppliesDecayPromotionAndCleanup() {
        ShortTermMemory shortTermMemory = mock(ShortTermMemory.class);
        LongTermMemory longTermMemory = mock(LongTermMemory.class);
        MemoryRetentionService retentionService = mock(MemoryRetentionService.class);
        MemoryProperties properties = new MemoryProperties();
        MemoryEntry candidate = new MemoryEntry();
        candidate.setTenantId("tenant-1");
        candidate.setUserId("user-1");
        LocalDateTime now = LocalDateTime.now();
        UserProfileMemoryRefreshService refreshService = mock(UserProfileMemoryRefreshService.class);
        when(shortTermMemory.applyDecay(now, 20)).thenReturn(new MemoryDecayResult(2, 1));
        when(longTermMemory.applyDecay(now, 20)).thenReturn(new MemoryDecayResult(3, 0));
        when(shortTermMemory.findPromotionCandidates(now, 20)).thenReturn(List.of(candidate));
        when(longTermMemory.savePromoted(candidate)).thenReturn(candidate);
        when(retentionService.cleanupExpiredMemories(now, 20)).thenReturn(4);
        MemoryDecayScheduler scheduler = new MemoryDecayScheduler(shortTermMemory, longTermMemory,
                retentionService, properties, new SimpleMeterRegistry(), refreshService);

        MemoryMaintenanceResult result = scheduler.maintainMemories(now, 20);

        assertEquals(2, result.shortTermUpdatedCount());
        assertEquals(1, result.shortTermDeletedCount());
        assertEquals(3, result.longTermUpdatedCount());
        assertEquals(1, result.promotedCount());
        assertEquals(4, result.expiredDeletedCount());
        verify(longTermMemory).savePromoted(candidate);
        verify(refreshService).refresh("tenant-1", "user-1");
    }
}
