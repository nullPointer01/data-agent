package com.ai.service.knowledge;

import com.ai.service.VectorMemoryService;

import com.ai.model.KnowledgeEntry;
import com.ai.rag.fulltext.FullTextDocument;
import com.ai.rag.fulltext.FullTextIndexService;
import com.ai.vector.TextChunker;
import com.ai.vector.VectorChunk;
import com.ai.vector.VectorDocumentTypes;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private final TextChunker textChunker;
    private final FullTextIndexService fullTextIndexService;

    public KnowledgeVectorIndexService(VectorMemoryService vectorMemoryService,
            TextChunker textChunker,
            FullTextIndexService fullTextIndexService) {
        this.vectorMemoryService = vectorMemoryService;
        this.textChunker = textChunker;
        this.fullTextIndexService = fullTextIndexService;
    }

    public void index(KnowledgeEntry entry) {
        vectorMemoryService.indexKnowledge(entry.getContent(), entry.getKnowledgeId(),
                entry.getTenantId(), entry.getCreatedBy());
        indexFullText(entry.getKnowledgeId(), entry.getTenantId(), entry.getCreatedBy(), entry.getName(),
                entry.getContent());
    }

    public void index(KnowledgeVectorEvent event) {
        vectorMemoryService.indexKnowledge(event.content(), event.knowledgeId(), event.tenantId(), event.userId());
        indexFullText(event.knowledgeId(), event.tenantId(), event.userId(), event.title(), event.content());
    }

    public void remove(KnowledgeEntry entry) {
        vectorMemoryService.removeFromStore(VectorDocumentTypes.KNOWLEDGE, entry.getKnowledgeId(), entry.getTenantId());
        fullTextIndexService.deleteBySource(VectorDocumentTypes.KNOWLEDGE, entry.getKnowledgeId(),
                entry.getTenantId());
    }

    public void remove(String knowledgeId, String tenantId) {
        vectorMemoryService.removeFromStore(VectorDocumentTypes.KNOWLEDGE, knowledgeId, tenantId);
        fullTextIndexService.deleteBySource(VectorDocumentTypes.KNOWLEDGE, knowledgeId, tenantId);
    }

    public void reindex(KnowledgeEntry entry) {
        remove(entry);
        index(entry);
    }

    public void reindex(KnowledgeVectorEvent event) {
        remove(event.knowledgeId(), event.tenantId());
        index(event);
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

    private void indexFullText(String knowledgeId, String tenantId, String userId, String title, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        List<FullTextDocument> documents = textChunker.split(knowledgeId, content).stream()
                .map(chunk -> toFullTextDocument(chunk, tenantId, userId, title, content))
                .toList();
        fullTextIndexService.index(documents);
    }

    private FullTextDocument toFullTextDocument(VectorChunk chunk, String tenantId, String userId, String title,
            String sourceContent) {
        return new FullTextDocument(VectorDocumentTypes.KNOWLEDGE, chunk.sourceId(), chunk.id(), tenantId,
                userId, title, chunk.text(), chunk.metadata(), parentContext(chunk, sourceContent));
    }

    private String parentContext(VectorChunk chunk, String sourceContent) {
        if (!chunk.metadata().hasParent() || !StringUtils.hasText(sourceContent)) {
            return "";
        }
        int start = Math.max(0, Math.min(chunk.parentCharStart(), sourceContent.length()));
        int end = Math.max(start, Math.min(chunk.parentCharEnd(), sourceContent.length()));
        return sourceContent.substring(start, end).trim();
    }
}
