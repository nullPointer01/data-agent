package com.ai.service.knowledge;

import com.ai.service.VectorMemoryService;

import com.ai.model.KnowledgeEntry;
import com.ai.rag.fulltext.FullTextDocument;
import com.ai.rag.fulltext.FullTextIndexService;
import com.ai.vector.SmartTextChunker;
import com.ai.vector.TextChunker;
import com.ai.vector.VectorDocumentTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeVectorIndexServiceTest {

    @Test
    void indexWritesVectorAndFullTextDocuments() {
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        CapturingFullTextIndexService fullTextIndexService = new CapturingFullTextIndexService();
        KnowledgeVectorIndexService service = new KnowledgeVectorIndexService(
                vectorMemoryService, new TextChunker(120, 20, new SmartTextChunker()), fullTextIndexService);
        KnowledgeEntry entry = knowledgeEntry();

        service.index(entry);

        verify(vectorMemoryService).indexKnowledge(entry.getContent(), "knowledge-1", "tenant-1", "user-1");
        assertFalse(fullTextIndexService.documents.isEmpty());
        assertEquals(VectorDocumentTypes.KNOWLEDGE, fullTextIndexService.documents.get(0).sourceType());
        assertEquals("knowledge-1", fullTextIndexService.documents.get(0).sourceId());
        assertEquals("客户分析", fullTextIndexService.documents.get(0).title());
    }

    @Test
    void removeDeletesVectorAndFullTextDocuments() {
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        CapturingFullTextIndexService fullTextIndexService = new CapturingFullTextIndexService();
        KnowledgeVectorIndexService service = new KnowledgeVectorIndexService(
                vectorMemoryService, new TextChunker(120, 20, new SmartTextChunker()), fullTextIndexService);

        service.remove("knowledge-1", "tenant-1");

        verify(vectorMemoryService).removeFromStore(VectorDocumentTypes.KNOWLEDGE, "knowledge-1", "tenant-1");
        assertEquals(VectorDocumentTypes.KNOWLEDGE, fullTextIndexService.deletedSourceType);
        assertEquals("knowledge-1", fullTextIndexService.deletedSourceId);
        assertEquals("tenant-1", fullTextIndexService.deletedTenantId);
    }

    private KnowledgeEntry knowledgeEntry() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setKnowledgeId("knowledge-1");
        entry.setTenantId("tenant-1");
        entry.setCreatedBy("user-1");
        entry.setName("客户分析");
        entry.setContent("客户流失主要来自售后响应慢，需要提升响应速度。");
        return entry;
    }

    private static final class CapturingFullTextIndexService implements FullTextIndexService {

        private List<FullTextDocument> documents = List.of();
        private String deletedSourceType;
        private String deletedSourceId;
        private String deletedTenantId;

        @Override
        public void index(List<FullTextDocument> documents) {
            this.documents = documents;
        }

        @Override
        public void deleteBySource(String sourceType, String sourceId, String tenantId) {
            this.deletedSourceType = sourceType;
            this.deletedSourceId = sourceId;
            this.deletedTenantId = tenantId;
        }
    }
}
