package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import com.ai.memory.dto.MemoryContext;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.security.SecurityContextHelper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryManagerTest {

    @Test
    void captureRoutesWorkingMemoryToRedisLayer() {
        WorkingMemory workingMemory = mock(WorkingMemory.class);
        ShortTermMemory shortTermMemory = mock(ShortTermMemory.class);
        LongTermMemory longTermMemory = mock(LongTermMemory.class);
        SecurityContextHelper securityContextHelper = identity();
        MemoryManager manager = new MemoryManager(workingMemory, shortTermMemory, longTermMemory,
                securityContextHelper, new SimpleMeterRegistry(), mock(MemoryQuotaService.class),
                mock(UserProfileMemoryService.class), mock(UserProfileMemoryRefreshService.class));
        MemoryCaptureRequest request = new MemoryCaptureRequest(
                MemoryTier.WORKING, MemoryType.INTENT, MemorySource.SYSTEM_GENERATED,
                "session-1", "state", null, null, null, null, 3);

        MemoryEntry entry = manager.capture(request);

        assertNull(entry);
        verify(workingMemory).save("tenant-1", "user-1", "session-1", "state");
    }

    @Test
    void buildContextAggregatesThreeMemoryLayers() {
        WorkingMemory workingMemory = mock(WorkingMemory.class);
        ShortTermMemory shortTermMemory = mock(ShortTermMemory.class);
        LongTermMemory longTermMemory = mock(LongTermMemory.class);
        when(workingMemory.get("tenant-1", "user-1", "session-1")).thenReturn("working");
        UserProfileMemoryService userProfileMemoryService = mock(UserProfileMemoryService.class);
        when(longTermMemory.recall("tenant-1", "user-1", "query", 5)).thenReturn("long-term");
        when(userProfileMemoryService.getSnapshot("tenant-1", "user-1")).thenReturn(
                new UserMemoryProfileSnapshotResponse("张三", null, null, null, null,
                        "表格优先", java.util.List.of(), java.util.List.of(), java.util.List.of(), 0.7D));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MemoryManager manager = new MemoryManager(workingMemory, shortTermMemory, longTermMemory, identity(),
                meterRegistry, mock(MemoryQuotaService.class), userProfileMemoryService,
                mock(UserProfileMemoryRefreshService.class));

        MemoryContext context = manager.buildContext("session-1", "query");

        assertEquals("working", context.workingMemory());
        assertEquals("long-term", context.longTermContext());
        assertEquals("张三", context.userProfile().displayName());
        verify(shortTermMemory).recall("tenant-1", "user-1", 5);
        assertEquals(1L, meterRegistry.timer("data_agent_memory_context_duration").count());
    }

    @Test
    void captureLongTermMemoryRefreshesPersistedUserProfile() {
        WorkingMemory workingMemory = mock(WorkingMemory.class);
        ShortTermMemory shortTermMemory = mock(ShortTermMemory.class);
        LongTermMemory longTermMemory = mock(LongTermMemory.class);
        UserProfileMemoryService userProfileMemoryService = mock(UserProfileMemoryService.class);
        UserProfileMemoryRefreshService refreshService = mock(UserProfileMemoryRefreshService.class);
        MemoryCaptureRequest request = new MemoryCaptureRequest(
                MemoryTier.LONG_TERM, MemoryType.PREFERENCE, MemorySource.USER_EXPLICIT,
                "session-1", "我叫张三，喜欢表格", null, null, null, null, 5);
        MemoryEntry saved = new MemoryEntry();
        saved.setMemoryId("m-1");
        saved.setTenantId("tenant-1");
        saved.setUserId("user-1");
        saved.setTier(MemoryTier.LONG_TERM);
        saved.setType(MemoryType.PREFERENCE);
        saved.setContent("我叫张三，喜欢表格");
        when(longTermMemory.capture("tenant-1", "user-1", request.normalize())).thenReturn(saved);
        MemoryManager manager = new MemoryManager(workingMemory, shortTermMemory, longTermMemory, identity(),
                new SimpleMeterRegistry(), mock(MemoryQuotaService.class), userProfileMemoryService,
                refreshService);

        MemoryEntry entry = manager.capture(request);

        assertEquals("m-1", entry.getMemoryId());
        verify(refreshService).refresh("tenant-1", "user-1");
    }

    private SecurityContextHelper identity() {
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(securityContextHelper.getCurrentUserId()).thenReturn("user-1");
        return securityContextHelper;
    }
}
