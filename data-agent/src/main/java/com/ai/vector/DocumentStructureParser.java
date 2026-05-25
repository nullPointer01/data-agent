package com.ai.vector;

import java.util.List;

/**
 * 文档结构解析器。
 *
 * @author data-agent
 */
public interface DocumentStructureParser {

    /**
     * 将原始文本解析为带章节路径和字符位置的结构片段。
     *
     * @param content 原始文本
     * @return 结构片段
     */
    List<DocumentStructureSegment> parse(String content);
}
