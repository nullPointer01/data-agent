package com.ai.service.knowledge;

import com.ai.service.VectorMemoryService;

import com.ai.service.file.FileParserService;

import com.ai.repository.KnowledgeEntryRepository;
import com.ai.rag.retrieval.HybridRetriever;
import com.ai.rag.RagQueryAnalysis;
import com.ai.rag.RagQueryRewriter;
import com.ai.rag.RagReranker;
import com.ai.rag.retrieval.RetrievalResult;
import com.ai.security.SecurityContextHelper;
import com.ai.vector.ChunkMetadata;
import com.ai.vector.VectorDocumentTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeServiceSearchTest {

    @Test
    void searchKnowledgeReturnsStructuredResults() {
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        HybridRetriever hybridRetriever = mock(HybridRetriever.class);
        KnowledgeVectorIndexService indexService = mock(KnowledgeVectorIndexService.class);
        KnowledgeService service = new KnowledgeService(
                mock(KnowledgeEntryRepository.class),
                securityContextHelper,
                mock(FileParserService.class),
                indexService,
                mock(KnowledgeVectorEventPublisher.class),
                hybridRetriever,
                new RagQueryRewriter(),
                new RagReranker());
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(hybridRetriever.retrieve(any(RagQueryAnalysis.class), eq("tenant-1"), eq(3), eq(0.45D),
                eq(List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE))))
                .thenReturn(List.of(new RetrievalResult(VectorDocumentTypes.KNOWLEDGE, "knowledge-1",
                        "chunk-1", "客户流失来自售后响应慢", 0.89D, 0.89D, null, List.of("vector"),
                        new ChunkMetadata("年度报告 > 客户分析", 100, 130, false, false, true))));

        var response = service.searchKnowledge("客户流失", 3);

        assertTrue(response.success());
        assertEquals(1, response.results().size());
        assertEquals("knowledge-1", response.results().get(0).sourceId());
        assertEquals("chunk-1", response.results().get(0).chunkId());
        assertEquals(0.89D, response.results().get(0).score());
        assertEquals(0.89D, response.results().get(0).vectorScore());
        assertTrue(response.results().get(0).channels().contains("vector"));
        assertEquals("客户流失来自售后响应慢", response.results().get(0).content());
        assertEquals("年度报告 > 客户分析", response.results().get(0).sectionPath());
        assertEquals(100, response.results().get(0).charStart());
        assertEquals(130, response.results().get(0).charEnd());
        assertTrue(response.results().get(0).containsList());
    }
}
