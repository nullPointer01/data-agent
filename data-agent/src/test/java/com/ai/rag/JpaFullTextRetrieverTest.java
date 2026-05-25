package com.ai.rag;
import com.ai.rag.fulltext.FullTextSearchResult;
import com.ai.rag.fulltext.JpaFullTextRetriever;

import com.ai.model.FileMetadata;
import com.ai.model.FileProcessingStatus;
import com.ai.model.KnowledgeEntry;
import com.ai.repository.FileMetadataRepository;
import com.ai.repository.KnowledgeEntryRepository;
import com.ai.vector.SmartTextChunker;
import com.ai.vector.TextChunker;
import com.ai.vector.VectorDocumentTypes;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JpaFullTextRetrieverTest {

    @Test
    void retrieveReturnsKnowledgeAndFileHits() {
        KnowledgeEntryRepository knowledgeRepository = mock(KnowledgeEntryRepository.class);
        FileMetadataRepository fileMetadataRepository = mock(FileMetadataRepository.class);
        JpaFullTextRetriever retriever = new JpaFullTextRetriever(
                knowledgeRepository, fileMetadataRepository, new TextChunker(120, 20, new SmartTextChunker()));
        RagQueryAnalysis analysis = new RagQueryAnalysis("客户流失", "客户流失",
                List.of("客户", "流失", "售后"), "ANALYSIS", List.of("客户流失"));
        when(knowledgeRepository.searchByTenantAndKeyword(eq("tenant-1"), eq("%客户流失%"), any(Pageable.class)))
                .thenReturn(List.of(knowledgeEntry()));
        when(fileMetadataRepository.searchByTenantAndKeyword(eq("tenant-1"), eq(FileProcessingStatus.COMPLETED.name()),
                eq("%客户流失%"), any(Pageable.class))).thenReturn(List.of(fileMetadata()));

        List<FullTextSearchResult> results = retriever.retrieve(analysis, "tenant-1", 5,
                List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE));

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(result -> VectorDocumentTypes.KNOWLEDGE.equals(result.sourceType())));
        assertTrue(results.stream().anyMatch(result -> VectorDocumentTypes.FILE.equals(result.sourceType())));
        assertTrue(results.stream().anyMatch(result -> result.sectionPath().contains("客户分析")));
    }

    @Test
    void retrieveHonorsSourceTypeFilter() {
        KnowledgeEntryRepository knowledgeRepository = mock(KnowledgeEntryRepository.class);
        FileMetadataRepository fileMetadataRepository = mock(FileMetadataRepository.class);
        JpaFullTextRetriever retriever = new JpaFullTextRetriever(
                knowledgeRepository, fileMetadataRepository, new TextChunker(120, 20, new SmartTextChunker()));
        RagQueryAnalysis analysis = new RagQueryAnalysis("客户流失", "客户流失",
                List.of("客户", "流失"), "ANALYSIS", List.of("客户流失"));
        when(knowledgeRepository.searchByTenantAndKeyword(eq("tenant-1"), eq("%客户流失%"), any(Pageable.class)))
                .thenReturn(List.of(knowledgeEntry()));

        List<FullTextSearchResult> results = retriever.retrieve(analysis, "tenant-1", 5,
                List.of(VectorDocumentTypes.KNOWLEDGE));

        assertEquals(1, results.size());
        assertEquals(VectorDocumentTypes.KNOWLEDGE, results.get(0).sourceType());
    }

    private KnowledgeEntry knowledgeEntry() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setKnowledgeId("knowledge-1");
        entry.setName("客户流失分析");
        entry.setDescription("售后响应慢会影响客户留存");
        entry.setContent("""
                # 客户分析

                客户流失主要来自售后响应慢，需要提升响应速度。
                """);
        return entry;
    }

    private FileMetadata fileMetadata() {
        FileMetadata metadata = new FileMetadata();
        metadata.setFileId("file-1");
        metadata.setFilename("客户流失报表.txt");
        metadata.setContent("客户流失与售后响应速度直接相关。");
        metadata.setProcessingStatus(FileProcessingStatus.COMPLETED);
        return metadata;
    }
}
