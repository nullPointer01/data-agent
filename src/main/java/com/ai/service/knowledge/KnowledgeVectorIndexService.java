package com.ai.service.knowledge;

import com.ai.service.VectorMemoryService;
import com.ai.rag.indexing.RagDocumentIndexer;

import com.ai.model.KnowledgeEntry;
import com.ai.vector.VectorDocumentTypes;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 协调知识条目的向量索引。
 *
 * @author data-agent
 */
@Service
public class KnowledgeVectorIndexService {

    private final VectorMemoryService vectorMemoryService;
    private final RagDocumentIndexer documentIndexer;

    public KnowledgeVectorIndexService(VectorMemoryService vectorMemoryService,
            RagDocumentIndexer documentIndexer) {
        this.vectorMemoryService = vectorMemoryService;
        this.documentIndexer = documentIndexer;
    }

    public void index(KnowledgeEntry entry) {
        documentIndexer.replace(VectorDocumentTypes.KNOWLEDGE, entry.getKnowledgeId(), entry.getName(),
                entry.getContent(), entry.getTenantId(), entry.getCreatedBy());
    }

    public void remove(KnowledgeEntry entry) {
        remove(entry.getKnowledgeId(), entry.getTenantId());
    }

    public void remove(String knowledgeId, String tenantId) {
        documentIndexer.remove(VectorDocumentTypes.KNOWLEDGE, knowledgeId, tenantId);
    }

    public void reindex(KnowledgeEntry entry) {
        index(entry);
    }

    public int estimateChunkCount(String content) {
        return vectorMemoryService.estimateChunkCount(content);
    }

    public int getIndexedCount() {
        return vectorMemoryService.getIndexedCount();
    }

    public Map<String, Long> getIndexedCountByType() {
        return vectorMemoryService.getIndexedCountByType();
    }

    public String searchRelevant(String query, int topK, double minScore, String tenantId) {
        return vectorMemoryService.searchRelevant(query, topK, minScore, tenantId);
    }

    public List<EmbeddingMatch<TextSegment>> searchMatches(String query, int topK, double minScore, String tenantId) {
        return vectorMemoryService.searchMatches(query, topK, minScore, tenantId,
                List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE));
    }

    public boolean isUsingMilvus() {
        return vectorMemoryService.isUsingMilvus();
    }

}
