package com.ai.memory;

import com.ai.memory.dto.MemoryStatsResponse;
import com.ai.memory.dto.MemoryListResponse;
import com.ai.memory.dto.MemoryMutationResponse;
import com.ai.memory.dto.UserMemoryProfileResponse;
import com.ai.repository.MemoryEntryRepository;
import com.ai.repository.UserProfileMemoryRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.service.AuditLogService;
import com.ai.service.VectorMemoryService;
import com.ai.vector.VectorDocumentTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryGovernanceServiceTest {

    @Test
    void listCurrentUserMemoriesUsesTenantUserScope() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        MemoryGovernanceService service = newService(repository);
        MemoryEntry entry = memory("m-1", MemoryTier.SHORT_TERM, MemoryType.SUMMARY, "summary");
        when(repository.findByTenantIdAndUserIdAndTierOrderByCreatedAtDesc(
                eq("tenant-1"), eq("user-1"), eq(MemoryTier.SHORT_TERM), any(Pageable.class)))
                .thenReturn(List.of(entry));
        when(repository.countByTenantIdAndUserIdAndTier("tenant-1", "user-1", MemoryTier.SHORT_TERM))
                .thenReturn(1L);

        MemoryListResponse response = service.listCurrentUserMemories(MemoryTier.SHORT_TERM, 20);

        assertTrue(response.success());
        assertEquals(1L, response.total());
        assertEquals("summary", response.memories().get(0).content());
    }

    @Test
    void getCurrentUserProfileAggregatesLongTermMemoryByType() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        MemoryGovernanceService service = newService(repository);
        when(repository.findByTenantIdAndUserIdOrderByCreatedAtDesc(
                eq("tenant-1"), eq("user-1"), any(Pageable.class)))
                .thenReturn(List.of(
                        memory("m-1", MemoryTier.LONG_TERM, MemoryType.PREFERENCE, "我喜欢简洁回答，默认用表格"),
                        memory("m-2", MemoryTier.LONG_TERM, MemoryType.ENTITY, "我叫张三，我是电商运营"),
                        memory("m-3", MemoryTier.LONG_TERM, MemoryType.CONCLUSION, "经常分析销售报表")));
        when(repository.countByTenantIdAndUserId("tenant-1", "user-1")).thenReturn(3L);

        UserMemoryProfileResponse response = service.getCurrentUserProfile();

        assertTrue(response.success());
        assertEquals(1, response.preferences().size());
        assertEquals(1, response.profileFacts().size());
        assertEquals(1, response.conclusions().size());
        assertEquals("张三", response.profile().displayName());
        assertEquals("电商运营", response.profile().role());
        assertEquals("表格优先", response.profile().preferredFormat());
        assertTrue(response.profile().frequentlyAskedTopics().contains("销售分析"));
        assertEquals(3L, response.totalMemories());
    }

    @Test
    void deleteCurrentUserMemoryRemovesLongTermVectorChunks() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        MemoryGovernanceService service = newService(repository, vectorMemoryService, auditLogService);
        MemoryEntry entry = memory("m-1", MemoryTier.LONG_TERM, MemoryType.PREFERENCE, "喜欢表格");
        when(repository.findByMemoryIdAndTenantIdAndUserId("m-1", "tenant-1", "user-1"))
                .thenReturn(Optional.of(entry));

        MemoryMutationResponse response = service.deleteCurrentUserMemory("m-1");

        assertTrue(response.success());
        assertEquals(1, response.affectedCount());
        verify(vectorMemoryService).removeFromStore(VectorDocumentTypes.MEMORY, "m-1", "tenant-1");
        verify(repository).delete(entry);
        verify(auditLogService).record(eq("DELETE_MEMORY"), eq("memory"), eq("m-1"), eq("SUCCESS"), any());
    }

    @Test
    void getCurrentUserStatsReturnsQuotaProfileAndContextMetrics() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MemoryProperties memoryProperties = new MemoryProperties();
        memoryProperties.setMaxMemoriesPerUser(20);
        memoryProperties.setShortTermRetentionDays(14);
        memoryProperties.setLongTermDecayAfterDays(120);
        MemoryGovernanceService service = newService(repository, mock(VectorMemoryService.class),
                mock(AuditLogService.class), memoryProperties, meterRegistry);
        meterRegistry.timer("data_agent_memory_context_duration").record(25, java.util.concurrent.TimeUnit.MILLISECONDS);
        when(repository.countByTenantIdAndUserId("tenant-1", "user-1")).thenReturn(5L);
        when(repository.countByTenantIdAndUserIdAndTier("tenant-1", "user-1", MemoryTier.WORKING)).thenReturn(1L);
        when(repository.countByTenantIdAndUserIdAndTier("tenant-1", "user-1", MemoryTier.SHORT_TERM)).thenReturn(3L);
        when(repository.countByTenantIdAndUserIdAndTier("tenant-1", "user-1", MemoryTier.LONG_TERM)).thenReturn(1L);
        when(repository.countByTenantIdAndUserIdAndType("tenant-1", "user-1", MemoryType.SUMMARY)).thenReturn(3L);
        when(repository.countByTenantIdAndUserIdAndType("tenant-1", "user-1", MemoryType.PREFERENCE)).thenReturn(2L);
        when(repository.sumSourceContentLengthByTenantUser("tenant-1", "user-1")).thenReturn(1000L);
        when(repository.sumStoredContentLengthByTenantUser("tenant-1", "user-1")).thenReturn(250L);

        MemoryStatsResponse response = service.getCurrentUserStats();

        assertTrue(response.success());
        assertEquals(5L, response.totalMemories());
        assertEquals(1L, response.workingMemoryCount());
        assertEquals(3L, response.shortTermMemoryCount());
        assertEquals(1L, response.longTermMemoryCount());
        assertEquals(3L, response.typeCounts().get("SUMMARY"));
        assertEquals(2L, response.typeCounts().get("PREFERENCE"));
        assertEquals(1000L, response.sourceContentChars());
        assertEquals(250L, response.storedContentChars());
        assertEquals(0.25D, response.compressionRatio());
        assertEquals(0.75D, response.storageSavingRatio());
        assertEquals(20, response.maxMemoriesPerUser());
        assertEquals(0.25D, response.quotaUsage());
        assertEquals(14, response.shortTermRetentionDays());
        assertEquals(120, response.longTermDecayAfterDays());
        assertEquals(1L, response.contextBuildCount());
        assertEquals(25D, response.averageContextBuildMs());
    }

    private MemoryGovernanceService newService(MemoryEntryRepository repository) {
        return newService(repository, mock(VectorMemoryService.class), mock(AuditLogService.class));
    }

    private MemoryGovernanceService newService(MemoryEntryRepository repository,
            VectorMemoryService vectorMemoryService,
            AuditLogService auditLogService) {
        return newService(repository, vectorMemoryService, auditLogService, new MemoryProperties(),
                new SimpleMeterRegistry());
    }

    private MemoryGovernanceService newService(MemoryEntryRepository repository,
            VectorMemoryService vectorMemoryService,
            AuditLogService auditLogService,
            MemoryProperties memoryProperties,
            SimpleMeterRegistry meterRegistry) {
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(securityContextHelper.getCurrentUserId()).thenReturn("user-1");
        return new MemoryGovernanceService(repository, securityContextHelper, vectorMemoryService, auditLogService,
                new UserMemoryProfileExtractor(), userProfileService(), memoryProperties, meterRegistry);
    }

    private UserProfileMemoryService userProfileService() {
        UserProfileMemoryRepository repository = mock(UserProfileMemoryRepository.class);
        when(repository.save(any(UserProfileMemory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new UserProfileMemoryService(repository, new MemoryJsonCodec(new ObjectMapper()));
    }

    private MemoryEntry memory(String memoryId, MemoryTier tier, MemoryType type, String content) {
        MemoryEntry entry = new MemoryEntry();
        entry.setMemoryId(memoryId);
        entry.setTenantId("tenant-1");
        entry.setUserId("user-1");
        entry.setTier(tier);
        entry.setType(type);
        entry.setSource(MemorySource.USER_EXPLICIT);
        entry.setCompressedContent(content);
        entry.setDecayWeight(0.8D);
        return entry;
    }
}
