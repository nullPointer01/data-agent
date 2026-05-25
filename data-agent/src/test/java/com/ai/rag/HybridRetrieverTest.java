package com.ai.rag;
import com.ai.rag.retrieval.DefaultHybridRetriever;
import com.ai.rag.retrieval.RrfFusionRanker;
import com.ai.rag.retrieval.HybridRetriever;
import com.ai.rag.fulltext.FullTextRetriever;
import com.ai.rag.fulltext.FullTextSearchResult;

import com.ai.rag.retrieval.RetrievalResult;

import com.ai.service.VectorMemoryService;
import com.ai.vector.VectorDocumentTypes;
import com.ai.vector.VectorMetadataFactory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridRetrieverTest {

    @Test
    void retrieveFusesVectorAndFullTextResults() {
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        FullTextRetriever fullTextRetriever = mock(FullTextRetriever.class);
        DefaultHybridRetriever retriever = new DefaultHybridRetriever(vectorMemoryService, fullTextRetriever, new RrfFusionRanker());
        RagQueryAnalysis analysis = new RagQueryAnalysis("客户流失", "客户流失",
                List.of("客户", "流失"), "ANALYSIS", List.of("客户流失"));
        List<String> sourceTypes = List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE);
        when(vectorMemoryService.searchMatches("客户流失", 20, 0.45D, "tenant-1", sourceTypes))
                .thenReturn(List.of(match("chunk-1", "knowledge-1", "客户流失来自售后响应慢", 0.88D)));
        when(fullTextRetriever.retrieve(analysis, "tenant-1", 20, sourceTypes))
                .thenReturn(List.of(new FullTextSearchResult(VectorDocumentTypes.KNOWLEDGE, "knowledge-1",
                        "chunk-1", "客户流失来自售后响应慢", 0.91D)));

        List<RetrievalResult> results = retriever.retrieve(analysis, "tenant-1", 20, 0.45D, sourceTypes);

        assertEquals(1, results.size());
        assertEquals("knowledge-1", results.get(0).sourceId());
        assertEquals("chunk-1", results.get(0).chunkId());
        assertEquals(0.88D, results.get(0).vectorScore());
        assertEquals(0.91D, results.get(0).fullTextScore());
    }

    private EmbeddingMatch<TextSegment> match(String chunkId, String sourceId, String text, double score) {
        TextSegment segment = TextSegment.from(text,
                VectorMetadataFactory.create(VectorDocumentTypes.KNOWLEDGE, chunkId, sourceId,
                        "tenant-1", "user-1"));
        return new EmbeddingMatch<>(score, chunkId, null, segment);
    }
}
