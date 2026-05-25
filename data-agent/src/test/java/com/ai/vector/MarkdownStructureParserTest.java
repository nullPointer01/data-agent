package com.ai.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownStructureParserTest {

    private final MarkdownStructureParser parser = new MarkdownStructureParser();

    @Test
    void parseRecordsHeadingPathForNestedSections() {
        String content = """
                # 年度报告

                ## 客户分析

                售后响应慢导致客户流失。
                """;

        List<DocumentStructureSegment> segments = parser.parse(content);

        DocumentStructureSegment paragraph = findByText(segments, "售后响应慢");
        assertEquals(DocumentSegmentType.PARAGRAPH, paragraph.type());
        assertEquals("年度报告 > 客户分析", paragraph.sectionPath());
        assertTrue(paragraph.charEnd() > paragraph.charStart());
    }

    @Test
    void parseKeepsTableCodeAndListAsTypedSegments() {
        String content = """
                # 技术说明

                | 字段 | 含义 |
                | --- | --- |
                | gmv | 销售额 |

                ```sql
                SELECT * FROM orders;
                ```

                1. 上传文件
                2. 写入向量库
                """;

        List<DocumentStructureSegment> segments = parser.parse(content);

        assertEquals(DocumentSegmentType.TABLE, findByText(segments, "| gmv | 销售额 |").type());
        assertEquals(DocumentSegmentType.CODE, findByText(segments, "SELECT * FROM orders").type());
        assertEquals(DocumentSegmentType.LIST, findByText(segments, "1. 上传文件").type());
    }

    private DocumentStructureSegment findByText(List<DocumentStructureSegment> segments, String keyword) {
        return segments.stream()
                .filter(segment -> segment.text().contains(keyword))
                .findFirst()
                .orElseThrow();
    }
}
