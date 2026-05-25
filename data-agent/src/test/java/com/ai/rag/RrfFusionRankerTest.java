package com.ai.rag;
import com.ai.rag.fulltext.FullTextSearchResult;
import com.ai.rag.retrieval.RetrievalResult;
import com.ai.rag.retrieval.RrfFusionRanker;

import com.ai.vector.VectorDocumentTypes;
import com.ai.vector.ChunkMetadata;
import com.ai.vector.VectorChunk;
import com.ai.vector.VectorMetadataFactory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RrfFusionRankerTest {

    private final RrfFusionRanker ranker = new RrfFusionRanker();

    @Test
    void fuseMergesDuplicatedVectorAndFullTextChunk() {
        List<RetrievalResult> results = ranker.fuse(
                List.of(match("chunk-1", "knowledge-1", "客户流失来自售后响应慢", 0.88D)),
                List.of(new FullTextSearchResult(VectorDocumentTypes.KNOWLEDGE, "knowledge-1", "chunk-1",
                        "客户流失来自售后响应慢", 0.92D)),
                5);

        assertEquals(1, results.size());
        assertEquals("knowledge-1", results.get(0).sourceId());
        assertEquals("chunk-1", results.get(0).chunkId());
        assertEquals(0.88D, results.get(0).vectorScore());
        assertEquals(0.92D, results.get(0).fullTextScore());
        assertTrue(results.get(0).channels().contains("vector"));
        assertTrue(results.get(0).channels().contains("full_text"));
    }

    @Test
    void fusePreservesStructureMetadataFromVectorHit() {
        VectorChunk chunk = new VectorChunk("chunk-1", "knowledge-1", "客户流失来自售后响应慢",
                new ChunkMetadata("年度报告 > 客户分析", 10, 30, false, false, true,
                        "knowledge-1_parent_customer", 0, 80));
        TextSegment segment = TextSegment.from(chunk.text(),
                VectorMetadataFactory.create(VectorDocumentTypes.KNOWLEDGE, chunk, "tenant-1", "user-1"));

        List<RetrievalResult> results = ranker.fuse(
                List.of(new EmbeddingMatch<>(0.88D, chunk.id(), null, segment)), List.of(), 5);

        assertEquals("年度报告 > 客户分析", results.get(0).sectionPath());
        assertEquals(10, results.get(0).charStart());
        assertEquals(30, results.get(0).charEnd());
        assertEquals("knowledge-1_parent_customer", results.get(0).parentChunkId());
        assertTrue(results.get(0).containsList());
    }

    @Test
    void fuseReturnsFullTextOnlyResultsWhenVectorMisses() {
        List<RetrievalResult> results = ranker.fuse(
                List.of(),
                List.of(new FullTextSearchResult(VectorDocumentTypes.FILE, "file-1", "file-1_chunk_0",
                        "退款流程要求 24 小时内响应", 0.76D)),
                5);

        assertEquals(1, results.size());
        assertEquals(VectorDocumentTypes.FILE, results.get(0).sourceType());
        assertEquals("file-1", results.get(0).sourceId());
        assertEquals("file-1_chunk_0", results.get(0).chunkId());
        assertEquals(0.76D, results.get(0).fullTextScore());
    }

    private EmbeddingMatch<TextSegment> match(String chunkId, String sourceId, String text, double score) {
        TextSegment segment = TextSegment.from(text,
                VectorMetadataFactory.create(VectorDocumentTypes.KNOWLEDGE, chunkId, sourceId,
                        "tenant-1", "user-1"));
        return new EmbeddingMatch<>(score, chunkId, null, segment);
    }
}
