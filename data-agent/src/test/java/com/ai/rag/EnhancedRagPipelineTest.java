package com.ai.rag;
import com.ai.rag.retrieval.RetrievalResult;
import com.ai.rag.retrieval.HybridRetrievalResult;
import com.ai.rag.retrieval.HybridRetriever;

import com.ai.vector.VectorDocumentTypes;
import com.ai.vector.ChunkMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EnhancedRagPipelineTest {

    @Test
    void executeUsesRewrittenQueryForHybridSearch() {
        HybridRetriever hybridRetriever = mock(HybridRetriever.class);
        EnhancedRagPipeline pipeline = newPipeline(hybridRetriever, new RagProperties());
        when(hybridRetriever.retrieveWithTrace(any(RagQueryAnalysis.class), eq("tenant-1"), eq(20), eq(0.45D),
                eq(List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE))))
                .thenReturn(HybridRetrievalResult.empty());

        pipeline.execute("""
                [检索上下文]
                old context

                [用户问题]
                查询客户流失原因
                """, "tenant-1");

        verify(hybridRetriever).retrieveWithTrace(any(RagQueryAnalysis.class), eq("tenant-1"), eq(20), eq(0.45D),
                eq(List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE)));
    }

    @Test
    void executeReturnsStructuredCitations() {
        HybridRetriever hybridRetriever = mock(HybridRetriever.class);
        EnhancedRagPipeline pipeline = newPipeline(hybridRetriever, new RagProperties());
        when(hybridRetriever.retrieveWithTrace(any(RagQueryAnalysis.class), eq("tenant-1"), eq(20), eq(0.45D),
                eq(List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE))))
                .thenReturn(hybridResult(new RetrievalResult(VectorDocumentTypes.KNOWLEDGE, "knowledge-1",
                        "chunk-1", "客户流失主要来自售后响应慢。", 0.91D, 0.91D, null, List.of("vector"),
                        new ChunkMetadata("年度报告 > 客户分析", 100, 130, false, false, false))));

        var response = pipeline.execute("查询客户流失原因", "tenant-1");

        assertTrue(response.getContext().contains("资料 [R1]"));
        assertEquals(1, response.getHitCount());
        assertEquals("R1", response.getCitations().get(0).referenceId());
        assertEquals(VectorDocumentTypes.KNOWLEDGE, response.getCitations().get(0).sourceType());
        assertEquals("knowledge-1", response.getCitations().get(0).sourceId());
        assertEquals("chunk-1", response.getCitations().get(0).chunkId());
        assertEquals(0.91D, response.getCitations().get(0).score());
        assertEquals("年度报告 > 客户分析", response.getCitations().get(0).sectionPath());
        assertEquals(100, response.getCitations().get(0).charStart());
        assertEquals(130, response.getCitations().get(0).charEnd());
        assertEquals("ANALYSIS", response.getQueryType());
        assertTrue(response.getCitations().get(0).snippet().contains("售后响应慢"));
        assertTrue(response.getContext().contains("年度报告 > 客户分析"));
        assertEquals(1, response.getTrace().candidateCount());
        assertEquals(1, response.getTrace().finalCount());
        assertEquals(1, response.getTrace().vectorCandidateCount());
        assertEquals(0, response.getTrace().fullTextCandidateCount());
        assertEquals("jpa", response.getTrace().fullTextProvider());
        assertEquals("milvus", response.getTrace().vectorProvider());
        assertTrue(response.getCitations().get(0).channels().contains("vector"));
        assertEquals(0.91D, response.getCitations().get(0).vectorScore());
    }

    @Test
    void executeCompressesContextWithoutDroppingCitation() {
        HybridRetriever hybridRetriever = mock(HybridRetriever.class);
        RagProperties ragProperties = new RagProperties();
        ragProperties.setMaxContextChars(90);
        EnhancedRagPipeline pipeline = newPipeline(hybridRetriever, ragProperties);
        when(hybridRetriever.retrieveWithTrace(any(RagQueryAnalysis.class), eq("tenant-1"), eq(20), eq(0.45D),
                eq(List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE))))
                .thenReturn(hybridResult(new RetrievalResult(VectorDocumentTypes.KNOWLEDGE, "knowledge-1",
                        "chunk-1", "第一句说明客户流失原因。第二句说明售后响应慢。第三句说明改进方案。",
                        0.91D, 0.91D, null, List.of("vector"),
                        new ChunkMetadata("年度报告 > 客户分析", 100, 160, false, false, false))));

        var response = pipeline.execute("查询客户流失原因", "tenant-1");

        assertTrue(response.getContext().contains("资料 [R1]"));
        assertTrue(response.getContext().contains("[检索上下文已压缩]"));
        assertEquals(1, response.getCitations().size());
        assertTrue(response.getTrace().compressed());
        assertTrue(response.getTrace().contextChars() <= 90);
    }

    @Test
    void executeUsesResolvedParentContextWhenAvailable() {
        HybridRetriever hybridRetriever = mock(HybridRetriever.class);
        RagParentContextResolver resolver = mock(RagParentContextResolver.class);
        EnhancedRagPipeline pipeline = newPipeline(hybridRetriever, new RagProperties(), resolver);
        RetrievalResult childResult = new RetrievalResult(VectorDocumentTypes.KNOWLEDGE, "knowledge-1",
                "chunk-1", "售后响应慢。", 0.91D, 0.91D, null, List.of("vector"),
                new ChunkMetadata("年度报告 > 客户分析", 100, 106, false, false, false,
                        "knowledge-1_parent_customer", 80, 160));
        RetrievalResult parentResolved = new RetrievalResult(VectorDocumentTypes.KNOWLEDGE, "knowledge-1",
                "chunk-1", "售后响应慢。", 0.91D, 0.91D, null, List.of("vector"),
                childResult.metadata(), "第一段说明客户流失。第二段说明售后响应慢。第三段说明改进方案。");
        when(hybridRetriever.retrieveWithTrace(any(RagQueryAnalysis.class), eq("tenant-1"), eq(20), eq(0.45D),
                eq(List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE))))
                .thenReturn(hybridResult(childResult));
        when(resolver.resolve(childResult, "tenant-1")).thenReturn(parentResolved);

        var response = pipeline.execute("查询客户流失原因", "tenant-1");

        assertTrue(response.getContext().contains("第一段说明客户流失"));
        assertTrue(response.getContext().contains("第三段说明改进方案"));
        assertTrue(response.getCitations().get(0).snippet().contains("售后响应慢"));
        assertTrue(response.getCitations().get(0).parentContextUsed());
    }

    @Test
    void executeReturnsTraceWhenNoRagMatchSurvives() {
        HybridRetriever hybridRetriever = mock(HybridRetriever.class);
        EnhancedRagPipeline pipeline = newPipeline(hybridRetriever, new RagProperties());
        when(hybridRetriever.retrieveWithTrace(any(RagQueryAnalysis.class), eq("tenant-1"), eq(20), eq(0.45D),
                eq(List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE))))
                .thenReturn(hybridResult(new RetrievalResult("conversation", "session-1", "chunk-1",
                        "历史对话内容", 0.7D, 0.7D, null, List.of("vector"))));

        var response = pipeline.execute("查询客户流失原因", "tenant-1");

        assertEquals(0, response.getHitCount());
        assertEquals(1, response.getTrace().candidateCount());
        assertEquals(0, response.getTrace().finalCount());
        assertEquals(1, response.getTrace().vectorCandidateCount());
        assertEquals("ANALYSIS", response.getQueryType());
    }

    @Test
    void executeSkipsHybridSearchWhenTenantMissing() {
        HybridRetriever hybridRetriever = mock(HybridRetriever.class);
        EnhancedRagPipeline pipeline = newPipeline(hybridRetriever, new RagProperties());

        pipeline.execute("查询客户流失原因", "");

        verifyNoInteractions(hybridRetriever);
    }

    private EnhancedRagPipeline newPipeline(HybridRetriever hybridRetriever, RagProperties ragProperties) {
        return newPipeline(hybridRetriever, ragProperties, mock(RagParentContextResolver.class));
    }

    private EnhancedRagPipeline newPipeline(HybridRetriever hybridRetriever, RagProperties ragProperties,
            RagParentContextResolver resolver) {
        return new EnhancedRagPipeline(hybridRetriever, ragProperties, new RagQueryRewriter(), new RagReranker(),
                new RagContextCompressor(), resolver);
    }

    private HybridRetrievalResult hybridResult(RetrievalResult result) {
        return new HybridRetrievalResult(List.of(result), result.hasVectorScore() ? 1 : 0,
                result.hasFullTextScore() ? 1 : 0, result.hasVectorScore() ? 10L : 0L,
                result.hasFullTextScore() ? 12L : 0L, 1L);
    }
}
