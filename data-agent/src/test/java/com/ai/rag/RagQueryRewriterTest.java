package com.ai.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagQueryRewriterTest {

    private final RagQueryRewriter rewriter = new RagQueryRewriter();

    @Test
    void rewriteExtractsUserQuestionFromEnrichedPrompt() {
        String query = """
                [检索上下文]
                企业资料内容

                [用户问题]
                分析 Q3 销售额
                """;

        assertEquals("分析 Q3 销售额", rewriter.rewrite(query));
    }

    @Test
    void rewriteRemovesFileHint() {
        String query = "分析销售趋势\n\n[用户已上传文件数据，请使用工具获取数据]";

        assertEquals("分析销售趋势", rewriter.rewrite(query));
    }

    @Test
    void rewriteLimitsOverlongQueries() {
        String rewritten = rewriter.rewrite("a".repeat(500));

        assertEquals(300, rewritten.length());
    }

    @Test
    void rewriteReturnsBlankForBlankInput() {
        assertTrue(rewriter.rewrite(" ").isBlank());
    }
}
