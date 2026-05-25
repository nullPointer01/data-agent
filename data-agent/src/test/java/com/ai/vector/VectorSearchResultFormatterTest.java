package com.ai.vector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorSearchResultFormatterTest {

    @Test
    void formatUsesSourceIdAndReferenceNumber() {
        VectorSearchResultFormatter formatter = new VectorSearchResultFormatter();
        TextSegment segment = TextSegment.from("客户流失来自售后响应慢",
                VectorMetadataFactory.create(VectorDocumentTypes.KNOWLEDGE,
                        new VectorChunk("chunk-1", "knowledge-1", "客户流失来自售后响应慢",
                                "年度报告 > 客户分析", 100, 130, false, false, true),
                        "tenant-1", "user-1"));

        String result = formatter.format(new EmbeddingMatch<>(0.91D, "chunk-1", null, segment), 1);

        assertTrue(result.contains("[R1"));
        assertTrue(result.contains("knowledge-1"));
        assertTrue(result.contains("年度报告 > 客户分析"));
        assertTrue(result.contains("字符 100-130"));
        assertTrue(result.contains("列表"));
        assertTrue(result.contains("0.91"));
        assertTrue(result.contains("售后响应慢"));
    }
}
