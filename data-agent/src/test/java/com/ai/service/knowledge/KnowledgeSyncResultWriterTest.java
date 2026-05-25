package com.ai.service.knowledge;

import com.ai.model.KnowledgeEntry;
import com.ai.model.KnowledgeSyncConfig;
import com.ai.repository.KnowledgeEntryRepository;
import com.ai.repository.KnowledgeSyncConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeSyncResultWriterTest {

    @Test
    void saveSyncResultUpdatesEntryConfigAndPublishesReindex() {
        KnowledgeSyncConfigRepository syncConfigRepository = mock(KnowledgeSyncConfigRepository.class);
        KnowledgeEntryRepository knowledgeRepository = mock(KnowledgeEntryRepository.class);
        KnowledgeVectorIndexService indexService = mock(KnowledgeVectorIndexService.class);
        KnowledgeVectorEventPublisher eventPublisher = mock(KnowledgeVectorEventPublisher.class);
        KnowledgeSyncConfig config = syncConfig("kb-1", "tenant-1");
        KnowledgeEntry entry = knowledgeEntry("kb-1", "tenant-1");
        when(syncConfigRepository.findByKnowledgeIdAndTenantId("kb-1", "tenant-1"))
                .thenReturn(Optional.of(config));
        when(knowledgeRepository.findById("kb-1")).thenReturn(Optional.of(entry));
        when(indexService.estimateChunkCount("new content")).thenReturn(3);
        KnowledgeSyncSecurityService securityService = new KnowledgeSyncSecurityService(
                syncConfigRepository, knowledgeRepository, mock(com.ai.security.SecurityContextHelper.class));
        KnowledgeSyncResultWriter writer = new KnowledgeSyncResultWriter(
                syncConfigRepository,
                knowledgeRepository,
                indexService,
                eventPublisher,
                securityService,
                new TransactionTemplate(new NoopTransactionManager()));

        String result = writer.saveSyncResult(config, "new content");

        assertTrue(result.contains("同步成功"));
        assertEquals("new content", entry.getContent());
        assertEquals(3, entry.getChunkCount());
        assertEquals("成功", config.getLastSyncStatus());
        verify(knowledgeRepository).saveAndFlush(entry);
        verify(syncConfigRepository).save(config);
        verify(eventPublisher).publishReindex(entry);
    }

    private KnowledgeSyncConfig syncConfig(String knowledgeId, String tenantId) {
        KnowledgeSyncConfig config = new KnowledgeSyncConfig();
        config.setKnowledgeId(knowledgeId);
        config.setTenantId(tenantId);
        config.setSyncType(KnowledgeSyncPolicy.SYNC_TYPE_URL);
        config.setSourceUrl("https://example.com/kb.md");
        return config;
    }

    private KnowledgeEntry knowledgeEntry(String knowledgeId, String tenantId) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setKnowledgeId(knowledgeId);
        entry.setTenantId(tenantId);
        entry.setCreatedBy("user-1");
        return entry;
    }

    private static class NoopTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // Test transaction manager does not coordinate external resources.
        }

        @Override
        public void rollback(TransactionStatus status) {
            // Test transaction manager does not coordinate external resources.
        }
    }
}
