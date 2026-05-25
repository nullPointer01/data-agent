package com.ai.vector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkMetadataTest {

    @Test
    void mergeCombinesRangeAndStructureFlags() {
        ChunkMetadata left = new ChunkMetadata("年度报告", 10, 20, false, true, false);
        ChunkMetadata right = new ChunkMetadata("年度报告 > 客户分析", 18, 40, true, false, true);

        ChunkMetadata merged = left.merge(right);

        assertEquals("年度报告", merged.sectionPath());
        assertEquals(10, merged.charStart());
        assertEquals(40, merged.charEnd());
        assertTrue(merged.containsTable());
        assertTrue(merged.containsCode());
        assertTrue(merged.containsList());
    }

    @Test
    void plainUsesTextLengthAsRange() {
        ChunkMetadata metadata = ChunkMetadata.plain("销售内容");

        assertEquals(0, metadata.charStart());
        assertEquals(4, metadata.charEnd());
    }

    @Test
    void withParentMarksMetadataAsHierarchical() {
        ChunkMetadata metadata = ChunkMetadata.plain("销售内容")
                .withParent("doc_parent_root", 0, 20);

        assertEquals("doc_parent_root", metadata.parentChunkId());
        assertEquals(0, metadata.parentCharStart());
        assertEquals(20, metadata.parentCharEnd());
        assertTrue(metadata.hasParent());
    }
}
