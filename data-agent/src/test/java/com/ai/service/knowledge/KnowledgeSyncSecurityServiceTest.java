package com.ai.service.knowledge;

import com.ai.model.KnowledgeEntry;
import com.ai.repository.KnowledgeEntryRepository;
import com.ai.repository.KnowledgeSyncConfigRepository;
import com.ai.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeSyncSecurityServiceTest {

    @Test
    void assertCurrentTenantKnowledgeReturnsOwnedEntry() {
        KnowledgeEntryRepository knowledgeRepository = mock(KnowledgeEntryRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setKnowledgeId("kb-1");
        entry.setTenantId("tenant-1");
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(knowledgeRepository.findById("kb-1")).thenReturn(Optional.of(entry));

        KnowledgeSyncSecurityService service = new KnowledgeSyncSecurityService(
                mock(KnowledgeSyncConfigRepository.class), knowledgeRepository, securityContextHelper);

        KnowledgeEntry result = service.assertCurrentTenantKnowledge("kb-1");

        assertSame(entry, result);
    }

    @Test
    void assertTenantKnowledgeRejectsCrossTenantAccess() {
        KnowledgeEntryRepository knowledgeRepository = mock(KnowledgeEntryRepository.class);
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setKnowledgeId("kb-1");
        entry.setTenantId("tenant-2");
        when(knowledgeRepository.findById("kb-1")).thenReturn(Optional.of(entry));

        KnowledgeSyncSecurityService service = new KnowledgeSyncSecurityService(
                mock(KnowledgeSyncConfigRepository.class), knowledgeRepository, mock(SecurityContextHelper.class));

        assertThrows(SecurityException.class, () -> service.assertTenantKnowledge("kb-1", "tenant-1"));
    }
}
