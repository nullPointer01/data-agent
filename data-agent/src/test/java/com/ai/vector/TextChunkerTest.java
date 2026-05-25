package com.ai.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextChunkerTest {

    @Test
    void splitUsesSemanticBoundariesBeforeForcedOverlap() {
        TextChunker chunker = new TextChunker(5, 2);

        List<VectorChunk> chunks = chunker.split("doc", "a。b。c。d。");

        assertEquals(2, chunks.size());
        assertEquals("a。\nb。", chunks.get(0).text());
        assertEquals("c。\nd。", chunks.get(1).text());
    }

    @Test
    void splitFallsBackToOverlappedChunksForLongSegments() {
        TextChunker chunker = new TextChunker(5, 2);

        List<VectorChunk> chunks = chunker.split("doc", "abcdefghijklmnopqrstuvwxyz");

        assertEquals("abcde", chunks.get(0).text());
        assertEquals("defgh", chunks.get(1).text());
        assertEquals("vwxyz", chunks.get(chunks.size() - 1).text());
    }

    @Test
    void splitKeepsMarkdownHeadingWithNearbyParagraph() {
        TextChunker chunker = new TextChunker(30, 5);

        List<VectorChunk> chunks = chunker.split("doc", "# 标题\n\n第一段内容。\n第二段内容。");

        assertEquals(1, chunks.size());
        assertEquals("# 标题\n第一段内容。\n第二段内容。", chunks.get(0).text());
    }

    @Test
    void estimateChunkCountUsesSmartSplit() {
        TextChunker chunker = new TextChunker(10, 2);

        assertEquals(1, chunker.estimateChunkCount("第一句。第二句。"));
    }

    @Test
    void constructorRejectsInvalidOverlap() {
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(10, 10));
    }
}
