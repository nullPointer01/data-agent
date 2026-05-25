package com.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextCompressorTest {

    @Test
    void compressKeepsReferenceHeaderAndTruncatesAtReadableBoundary() {
        RagContextCompressor compressor = new RagContextCompressor();
        String content = "第一句说明客户流失原因。第二句说明售后响应慢。第三句说明改进方案。";

        String context = compressor.compress(List.of(new RagContextSection("R1", "资料 [R1] [knowledge]", content)),
                45);

        assertTrue(context.startsWith("资料 [R1]"));
        assertTrue(context.contains("客户流失原因。"));
        assertTrue(context.contains("[检索上下文已压缩]"));
        assertFalse(context.contains("第三句"));
    }

    @Test
    void compressSkipsSectionWhenHeaderCannotFit() {
        RagContextCompressor compressor = new RagContextCompressor();

        String context = compressor.compress(List.of(new RagContextSection("R1",
                "资料 [R1] [knowledge / source / chunk / very-long-section-name]", "正文")), 10);

        assertTrue(context.isBlank());
    }
}
