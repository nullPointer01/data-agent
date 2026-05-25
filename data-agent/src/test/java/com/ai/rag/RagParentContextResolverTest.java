package com.ai.rag;
import com.ai.rag.retrieval.RetrievalResult;

import com.ai.model.KnowledgeEntry;
import com.ai.repository.FileMetadataRepository;
import com.ai.repository.KnowledgeEntryRepository;
import com.ai.vector.ChunkMetadata;
import com.ai.vector.VectorDocumentTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagParentContextResolverTest {

    @Test
    void resolveSlicesParentContextFromKnowledgeContent() {
        KnowledgeEntryRepository knowledgeRepository = mock(KnowledgeEntryRepository.class);
        RagParentContextResolver resolver = new RagParentContextResolver(knowledgeRepository,
                mock(FileMetadataRepository.class));
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setContent("第一段说明客户流失。第二段说明售后响应慢。第三段说明改进方案。");
        when(knowledgeRepository.findByKnowledgeIdAndTenantId("knowledge-1", "tenant-1"))
                .thenReturn(Optional.of(entry));
        RetrievalResult result = new RetrievalResult(VectorDocumentTypes.KNOWLEDGE, "knowledge-1",
                "chunk-1", "第二段说明售后响应慢。", 0.91D, 0.91D, null, List.of("vector"),
                new ChunkMetadata("客户分析", 10, 20, false, false, false,
                        "knowledge-1_parent_customer", 0, entry.getContent().length()));

        RetrievalResult resolved = resolver.resolve(result, "tenant-1");

        assertEquals(entry.getContent(), resolved.parentContext());
    }
}
