package com.ai.memory;

import com.ai.mcp.McpModelService;
import com.ai.memory.dto.MemoryCompressionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelBackedMemoryCompressorTest {

    @Test
    void compressConversationUsesModelWhenEnabled() {
        McpModelService modelService = mock(McpModelService.class);
        MemoryProperties properties = new MemoryProperties();
        properties.setModelCompressionEnabled(true);
        when(modelService.callModel(org.mockito.ArgumentMatchers.anyString())).thenReturn("用户偏好用表格查看销售分析。");
        ModelBackedMemoryCompressor compressor = new ModelBackedMemoryCompressor(modelService,
                new DeterministicMemoryCompressor(), properties);

        MemoryCompressionResult result = compressor.compressConversation("请记住我喜欢表格", "好的");

        assertEquals("用户偏好用表格查看销售分析。", result.compressedContent());
    }

    @Test
    void compressConversationFallsBackWhenModelDisabled() {
        ModelBackedMemoryCompressor compressor = new ModelBackedMemoryCompressor(mock(McpModelService.class),
                new DeterministicMemoryCompressor(), new MemoryProperties());

        MemoryCompressionResult result = compressor.compressConversation("分析销售额", "增长 12%");

        assertTrue(result.compressedContent().contains("增长 12%"));
    }
}
