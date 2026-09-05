package com.ai.vector;

/**
 * 语义分块结果。
 *
 * @param text 分块文本
 * @param metadata 分块元数据
 * @author data-agent
 */
public record SemanticChunk(String text,
        ChunkMetadata metadata) {

    public SemanticChunk(String text, String sectionPath, int charStart, int charEnd,
            boolean containsTable, boolean containsCode, boolean containsList) {
        this(text, new ChunkMetadata(sectionPath, charStart, charEnd, containsTable, containsCode, containsList));
    }

    /**
     * 合并相邻语义块。
     *
     * @param next 下一个语义块
     * @return 合并后的语义块
     */
    public SemanticChunk merge(SemanticChunk next) {
        String mergedText = text + "\n" + next.text;
        return new SemanticChunk(mergedText, metadata.merge(next.metadata));
    }

    /**
     * 使用新文本和字符范围复制当前结构元数据。
     *
     * @param newText 新文本
     * @param newCharStart 新起始字符位置
     * @param newCharEnd 新结束字符位置
     * @return 新语义块
     */
    public SemanticChunk withText(String newText, int newCharStart, int newCharEnd) {
        return new SemanticChunk(newText, metadata.withRange(newCharStart, newCharEnd));
    }

    /**
     * 按偏移切出子块。
     *
     * @param sliceText 子块文本
     * @param startOffset 子块起始偏移
     * @param endOffset 子块结束偏移
     * @return 子块
     */
    public SemanticChunk slice(String sliceText, int startOffset, int endOffset) {
        return withText(sliceText, charStart() + startOffset, charStart() + endOffset);
    }

    public String sectionPath() {
        return metadata.sectionPath();
    }

    public int charStart() {
        return metadata.charStart();
    }

    public int charEnd() {
        return metadata.charEnd();
    }

    public boolean containsTable() {
        return metadata.containsTable();
    }

    public boolean containsCode() {
        return metadata.containsCode();
    }

    public boolean containsList() {
        return metadata.containsList();
    }
}
