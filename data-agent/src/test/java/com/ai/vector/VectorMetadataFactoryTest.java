package com.ai.vector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VectorMetadataFactoryTest {

    @Test
    void createIncludesChunkStructureMetadata() {
        VectorChunk chunk = new VectorChunk("chunk-1", "knowledge-1", "销售内容",
                new ChunkMetadata("年度报告 > 平台销售", 12, 28, true, false, true,
                        "knowledge-1_parent_sales", 0, 120));

        var metadata = VectorMetadataFactory.create(VectorDocumentTypes.KNOWLEDGE, chunk, "tenant-1", "user-1");

        assertEquals("chunk-1", metadata.getString(VectorMetadataKeys.ID));
        assertEquals("knowledge-1", metadata.getString(VectorMetadataKeys.SOURCE_ID));
        assertEquals("年度报告 > 平台销售", metadata.getString(VectorMetadataKeys.SECTION_PATH));
        assertEquals("12", metadata.getString(VectorMetadataKeys.CHAR_START));
        assertEquals("28", metadata.getString(VectorMetadataKeys.CHAR_END));
        assertEquals("true", metadata.getString(VectorMetadataKeys.CONTAINS_TABLE));
        assertEquals("false", metadata.getString(VectorMetadataKeys.CONTAINS_CODE));
        assertEquals("true", metadata.getString(VectorMetadataKeys.CONTAINS_LIST));
        assertEquals("knowledge-1_parent_sales", metadata.getString(VectorMetadataKeys.PARENT_CHUNK_ID));
        assertEquals("0", metadata.getString(VectorMetadataKeys.PARENT_CHAR_START));
        assertEquals("120", metadata.getString(VectorMetadataKeys.PARENT_CHAR_END));
    }
}
