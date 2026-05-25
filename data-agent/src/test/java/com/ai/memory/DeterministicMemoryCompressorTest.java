package com.ai.memory;

import com.ai.memory.dto.MemoryCompressionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicMemoryCompressorTest {

    @Test
    void compressConversationNormalizesWhitespaceAndBuildsSummary() {
        DeterministicMemoryCompressor compressor = new DeterministicMemoryCompressor();

        MemoryCompressionResult result = compressor.compressConversation(" 分析   GMV ", " 同比 增长  10% ");

        assertEquals("用户: 分析 GMV\n助手: 同比 增长 10%", result.content());
        assertEquals("用户提到：分析 GMV；助手回复：同比 增长 10%", result.compressedContent());
        assertEquals(List.of("conversation"), result.topicTags());
    }

    @Test
    void compressExplicitMemoryAddsPreferenceTags() {
        DeterministicMemoryCompressor compressor = new DeterministicMemoryCompressor();

        MemoryCompressionResult result = compressor.compressExplicitMemory("请记住，我偏好表格", MemoryType.PREFERENCE);

        assertEquals("请记住，我偏好表格", result.content());
        assertTrue(result.compressedContent().contains("用户明确要求记住"));
        assertEquals(List.of("user_memory", "preference"), result.topicTags());
    }
}
