package com.ai.service.knowledge;

import com.ai.exception.KnowledgeSyncException;
import com.ai.knowledge.dto.KnowledgeMutationResponse;
import com.ai.model.KnowledgeEntry;
import com.ai.model.KnowledgeSyncConfig;
import com.ai.repository.KnowledgeSyncConfigRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeSyncServiceTest {

    @Test
    void triggerSyncReturnsFailureWhenFetchedContentIsEmpty() {
        KnowledgeSyncConfigRepository syncConfigRepository = mock(KnowledgeSyncConfigRepository.class);
        KnowledgeSyncSecurityService securityService = mock(KnowledgeSyncSecurityService.class);
        KnowledgeSyncContentFetcher contentFetcher = mock(KnowledgeSyncContentFetcher.class);
        KnowledgeSyncConfig config = new KnowledgeSyncConfig();
        config.setKnowledgeId("kb-1");
        config.setSyncType(KnowledgeSyncPolicy.SYNC_TYPE_URL);
        config.setSourceUrl("https://example.com/kb.md");
        when(securityService.assertCurrentTenantKnowledge("kb-1")).thenReturn(new KnowledgeEntry());
        when(securityService.findOwnedSyncConfig("kb-1")).thenReturn(config);
        when(contentFetcher.fetch(KnowledgeSyncPolicy.SYNC_TYPE_URL, "https://example.com/kb.md"))
                .thenReturn(" ");
        KnowledgeSyncService service = new KnowledgeSyncService(
                syncConfigRepository,
                new KnowledgeSyncPolicy(),
                securityService,
                contentFetcher,
                mock(KnowledgeSyncResultWriter.class));

        KnowledgeMutationResponse response = service.triggerSync("kb-1");

        assertFalse(response.success());
        assertTrue(response.message().contains("获取内容为空"));
    }

    @Test
    void triggerSyncReturnsFailureWhenFetcherFails() {
        KnowledgeSyncSecurityService securityService = mock(KnowledgeSyncSecurityService.class);
        KnowledgeSyncContentFetcher contentFetcher = mock(KnowledgeSyncContentFetcher.class);
        KnowledgeSyncConfig config = new KnowledgeSyncConfig();
        config.setKnowledgeId("kb-1");
        config.setSyncType(KnowledgeSyncPolicy.SYNC_TYPE_URL);
        config.setSourceUrl("bad-url");
        when(securityService.assertCurrentTenantKnowledge("kb-1")).thenReturn(new KnowledgeEntry());
        when(securityService.findOwnedSyncConfig("kb-1")).thenReturn(config);
        when(contentFetcher.fetch(KnowledgeSyncPolicy.SYNC_TYPE_URL, "bad-url"))
                .thenThrow(new KnowledgeSyncException("同步地址格式错误"));
        KnowledgeSyncService service = new KnowledgeSyncService(
                mock(KnowledgeSyncConfigRepository.class),
                new KnowledgeSyncPolicy(),
                securityService,
                contentFetcher,
                mock(KnowledgeSyncResultWriter.class));

        KnowledgeMutationResponse response = service.triggerSync("kb-1");

        assertFalse(response.success());
        assertTrue(response.message().contains("同步地址格式错误"));
    }
}
