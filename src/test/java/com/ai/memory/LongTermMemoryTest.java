package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import com.ai.repository.MemoryEntryRepository;
import com.ai.service.VectorMemoryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongTermMemoryTest {

    @Test
    void shouldPruneOnlyWhenSemanticKeyCreatesNewEntry() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        MemoryEntryFactory factory = mock(MemoryEntryFactory.class);
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        MemoryQuotaService quotaService = mock(MemoryQuotaService.class);
        MemoryEntry incoming = memory("new-id", "新偏好");
        when(factory.create(any(), any(), any())).thenReturn(incoming);
        when(repository.findByTenantIdAndUserIdAndTypeAndSemanticKey(
                "tenant", "user", MemoryType.PREFERENCE, SemanticMemoryKey.OUTPUT_FORMAT))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LongTermMemory memory = new LongTermMemory(
                repository, factory, vectorMemoryService, quotaService, new MemoryProperties());

        memory.upsertSemantic("tenant", "user", request("新偏好"));

        verify(quotaService).pruneBeforeCapture("tenant", "user");
    }

    @Test
    void shouldNotPruneWhenUpdatingExistingSemanticKey() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        MemoryEntryFactory factory = mock(MemoryEntryFactory.class);
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        MemoryQuotaService quotaService = mock(MemoryQuotaService.class);
        MemoryEntry incoming = memory("incoming-id", "新偏好");
        MemoryEntry existing = memory("existing-id", "旧偏好");
        when(factory.create(any(), any(), any())).thenReturn(incoming);
        when(repository.findByTenantIdAndUserIdAndTypeAndSemanticKey(
                "tenant", "user", MemoryType.PREFERENCE, SemanticMemoryKey.OUTPUT_FORMAT))
                .thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LongTermMemory memory = new LongTermMemory(
                repository, factory, vectorMemoryService, quotaService, new MemoryProperties());

        memory.upsertSemantic("tenant", "user", request("新偏好"));

        verify(quotaService, never()).pruneBeforeCapture(any(), any());
    }

    private MemoryCaptureRequest request(String content) {
        return new MemoryCaptureRequest(
                MemoryTier.LONG_TERM,
                MemoryType.PREFERENCE,
                MemorySource.USER_EXPLICIT,
                "session",
                content,
                content,
                Map.of(),
                SemanticMemoryKey.OUTPUT_FORMAT,
                1D,
                List.of(),
                List.of(),
                5);
    }

    private MemoryEntry memory(String id, String content) {
        MemoryEntry entry = new MemoryEntry();
        entry.setMemoryId(id);
        entry.setContent(content);
        entry.setCompressedContent(content);
        entry.setType(MemoryType.PREFERENCE);
        entry.setSemanticKey(SemanticMemoryKey.OUTPUT_FORMAT);
        return entry;
    }
}
