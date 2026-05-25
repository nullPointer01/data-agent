package com.ai.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartTextChunkerTest {

    private final SmartTextChunker chunker = new SmartTextChunker();

    @Test
    void splitMergesParagraphsWithoutBreakingSemanticBoundaries() {
        List<VectorChunk> chunks = chunker.split("doc", "第一段。\n\n第二段。\n\n第三段。", 12, 2);

        assertEquals(2, chunks.size());
        assertEquals("第一段。\n第二段。", chunks.get(0).text());
        assertEquals("第三段。", chunks.get(1).text());
    }

    @Test
    void splitKeepsMarkdownHeadingAsBoundarySegment() {
        List<VectorChunk> chunks = chunker.split("doc", "# 销售分析\n\nGMV 增长。\n利润下降。", 40, 5);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).text().startsWith("# 销售分析"));
        assertEquals("销售分析", chunks.get(0).sectionPath());
    }

    @Test
    void splitOversizedTextWithOverlapWithoutDroppingTail() {
        List<VectorChunk> chunks = chunker.split("doc", "abcdefghijklmnopqrstuvwxyz", 8, 2);

        assertEquals(4, chunks.size());
        assertEquals("abcdefgh", chunks.get(0).text());
        assertEquals("ghijklmn", chunks.get(1).text());
        assertTrue(chunks.get(chunks.size() - 1).text().endsWith("yz"));
    }

    @Test
    void splitRecordsSectionPathAndCharacterRange() {
        String content = """
                # 年度报告

                ## 平台销售

                天猫销售额增长。
                京东销售额稳定。
                """;

        List<VectorChunk> chunks = chunker.split("doc", content, 80, 10);
        VectorChunk salesChunk = chunks.stream()
                .filter(chunk -> chunk.text().contains("天猫销售额增长"))
                .findFirst()
                .orElseThrow();

        assertEquals("年度报告 > 平台销售", salesChunk.sectionPath());
        assertTrue(salesChunk.charStart() >= 0);
        assertTrue(salesChunk.charEnd() > salesChunk.charStart());
    }

    @Test
    void splitKeepsMarkdownTableRowsTogetherWhenPossible() {
        String content = """
                # 销售数据

                | 平台 | 销售额 |
                | --- | --- |
                | 天猫 | 100 |
                | 京东 | 80 |

                结论：天猫领先。
                """;

        List<VectorChunk> chunks = chunker.split("doc", content, 80, 10);

        String tableChunk = findChunk(chunks, "| 天猫 | 100 |");
        assertTrue(tableChunk.contains("| 京东 | 80 |"));
        assertTrue(tableChunk.contains("| --- | --- |"));
        assertTrue(findVectorChunk(chunks, "| 天猫 | 100 |").containsTable());
    }

    @Test
    void splitKeepsCodeFenceAsAtomicSegment() {
        String content = """
                排查 SQL：

                ```sql
                SELECT * FROM orders;
                WHERE amount > 100;
                ```

                以上 SQL 只用于排查。
                """;

        List<VectorChunk> chunks = chunker.split("doc", content, 90, 10);

        String codeChunk = findChunk(chunks, "```sql");
        assertTrue(codeChunk.contains("WHERE amount > 100;"));
        assertTrue(codeChunk.contains("```"));
        assertTrue(findVectorChunk(chunks, "```sql").containsCode());
    }

    @Test
    void splitKeepsListItemsTogetherWhenPossible() {
        String content = """
                处理步骤：
                1. 上传文件
                2. 解析文本
                3. 写入向量库
                """;

        List<VectorChunk> chunks = chunker.split("doc", content, 80, 10);

        String listChunk = findChunk(chunks, "1. 上传文件");
        assertTrue(listChunk.contains("2. 解析文本"));
        assertTrue(listChunk.contains("3. 写入向量库"));
        assertTrue(findVectorChunk(chunks, "1. 上传文件").containsList());
    }

    @Test
    void forceSplitPrefersSentenceBoundary() {
        String content = "第一句说明客户流失。第二句说明售后响应慢。第三句说明改进方案。";

        List<VectorChunk> chunks = chunker.split("doc", content, 18, 4);

        assertTrue(chunks.get(0).text().endsWith("。"));
        assertTrue(chunks.get(1).text().contains("售后响应慢"));
        assertTrue(chunks.get(chunks.size() - 1).text().contains("改进方案"));
    }

    private String findChunk(List<VectorChunk> chunks, String keyword) {
        return findVectorChunk(chunks, keyword).text();
    }

    private VectorChunk findVectorChunk(List<VectorChunk> chunks, String keyword) {
        return chunks.stream()
                .filter(chunk -> chunk.text().contains(keyword))
                .findFirst()
                .orElseThrow();
    }
}
