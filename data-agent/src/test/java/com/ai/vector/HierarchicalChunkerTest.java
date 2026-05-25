package com.ai.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchicalChunkerTest {

    private final HierarchicalChunker chunker = new HierarchicalChunker();

    @Test
    void attachParentsGroupsChunksBySection() {
        List<VectorChunk> chunks = List.of(
                new VectorChunk("doc_chunk_0", "doc", "第一段。", "年度报告 > 客户分析", 10, 20,
                        false, false, false),
                new VectorChunk("doc_chunk_1", "doc", "第二段。", "年度报告 > 客户分析", 22, 32,
                        false, false, false),
                new VectorChunk("doc_chunk_2", "doc", "第三段。", "年度报告 > 平台销售", 60, 70,
                        false, false, false));

        List<VectorChunk> result = chunker.attachParents("doc", chunks, 40);

        assertEquals("doc_parent_年度报告_客户分析", result.get(0).parentChunkId());
        assertEquals(10, result.get(0).parentCharStart());
        assertEquals(32, result.get(0).parentCharEnd());
        assertEquals(10, result.get(1).parentCharStart());
        assertEquals(32, result.get(1).parentCharEnd());
        assertEquals("doc_parent_年度报告_平台销售", result.get(2).parentChunkId());
        assertEquals(60, result.get(2).parentCharStart());
        assertEquals(70, result.get(2).parentCharEnd());
    }

    @Test
    void smartTextChunkerAddsParentMetadata() {
        SmartTextChunker smartTextChunker = new SmartTextChunker();
        String content = """
                # 客户分析

                第一段说明客户流失。
                第二段说明售后响应慢。
                第三段说明改进方案。
                """;

        List<VectorChunk> chunks = smartTextChunker.split("doc", content, 18, 4);

        assertTrue(chunks.stream().allMatch(chunk -> chunk.metadata().hasParent()));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.parentChunkId().startsWith("doc_parent_客户分析")));
    }
}
