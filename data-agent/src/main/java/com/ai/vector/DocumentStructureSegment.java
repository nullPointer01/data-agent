package com.ai.vector;

/**
 * 从文档中解析出的结构片段。
 *
 * @param text 片段文本
 * @param sectionPath 标题路径
 * @param charStart 起始字符位置
 * @param charEnd 结束字符位置
 * @param type 片段类型
 * @author data-agent
 */
public record DocumentStructureSegment(String text,
        String sectionPath,
        int charStart,
        int charEnd,
        DocumentSegmentType type) {

    public boolean containsTable() {
        return DocumentSegmentType.TABLE == type;
    }

    public boolean containsCode() {
        return DocumentSegmentType.CODE == type;
    }

    public boolean containsList() {
        return DocumentSegmentType.LIST == type;
    }
}
