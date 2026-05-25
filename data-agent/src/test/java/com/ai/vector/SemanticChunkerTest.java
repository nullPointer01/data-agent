package com.ai.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticChunkerTest {

    private final SemanticChunker chunker = new SemanticChunker();

    @Test
    void chunkMergesSentencesWithoutBreakingSemanticBoundaries() {
        List<DocumentStructureSegment> segments = List.of(new DocumentStructureSegment(
                "第一段。第二段。第三段。", "", 0, 9, DocumentSegmentType.PARAGRAPH));

        List<SemanticChunk> chunks = chunker.chunk(segments, 12, 2);

        assertEquals(2, chunks.size());
        assertEquals("第一段。\n第二段。", chunks.get(0).text());
        assertEquals("第三段。", chunks.get(1).text());
    }

    @Test
    void chunkKeepsHeadingAsBoundarySegment() {
        List<DocumentStructureSegment> segments = List.of(
                new DocumentStructureSegment("# 销售分析", "销售分析", 0, 6, DocumentSegmentType.HEADING),
                new DocumentStructureSegment("GMV 增长。利润下降。", "销售分析", 8, 20, DocumentSegmentType.PARAGRAPH));

        List<SemanticChunk> chunks = chunker.chunk(segments, 40, 5);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).text().startsWith("# 销售分析"));
        assertEquals("销售分析", chunks.get(0).sectionPath());
    }

    @Test
    void chunkPreservesStructureFlags() {
        List<DocumentStructureSegment> segments = List.of(new DocumentStructureSegment(
                "| 平台 | 销售额 |\n| --- | --- |\n| 天猫 | 100 |",
                "年度报告 > 平台销售", 20, 60, DocumentSegmentType.TABLE));

        List<SemanticChunk> chunks = chunker.chunk(segments, 80, 10);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).containsTable());
        assertEquals("年度报告 > 平台销售", chunks.get(0).sectionPath());
    }

    @Test
    void chunkForceSplitPrefersSentenceBoundary() {
        List<DocumentStructureSegment> segments = List.of(new DocumentStructureSegment(
                "第一句说明客户流失。第二句说明售后响应慢。第三句说明改进方案。",
                "", 0, 33, DocumentSegmentType.PARAGRAPH));

        List<SemanticChunk> chunks = chunker.chunk(segments, 18, 4);

        assertTrue(chunks.get(0).text().endsWith("。"));
        assertTrue(chunks.get(1).text().contains("售后响应慢"));
        assertTrue(chunks.get(chunks.size() - 1).text().contains("改进方案"));
    }
}
