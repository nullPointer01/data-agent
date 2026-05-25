package com.ai.rag;

import com.ai.vector.VectorDocumentTypes;
import com.ai.vector.VectorMetadataFactory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagRerankerTest {

    private final RagReranker reranker = new RagReranker();

    @Test
    void rerankPromotesKeywordRichKnowledgeChunk() {
        EmbeddingMatch<TextSegment> weakButRelevant = match(VectorDocumentTypes.KNOWLEDGE,
                "售后响应慢导致客户流失", 0.78D);
        EmbeddingMatch<TextSegment> strongButVague = match(VectorDocumentTypes.FILE,
                "客户资料列表", 0.82D);
        RagQueryAnalysis analysis = new RagQueryAnalysis("客户流失原因", "客户流失原因",
                List.of("客户", "流失", "售后"), "ANALYSIS", List.of("客户流失原因"));

        List<EmbeddingMatch<TextSegment>> result = reranker.rerank(List.of(strongButVague, weakButRelevant),
                analysis);

        assertEquals(weakButRelevant, result.get(0));
    }

    private EmbeddingMatch<TextSegment> match(String type, String text, double score) {
        TextSegment segment = TextSegment.from(text,
                VectorMetadataFactory.create(type, text, "source-1", "tenant-1", "user-1"));
        return new EmbeddingMatch<>(score, text, null, segment);
    }
}
