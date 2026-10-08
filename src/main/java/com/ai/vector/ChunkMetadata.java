package com.ai.vector;

/**
 * 文本分块元数据。
 *
 * @param sectionPath 标题路径
 * @param charStart 原始正文 UTF-16 起始位置（含），框架可能调整范围内的空白
 * @param charEnd 原始正文 UTF-16 结束位置（不含）
 * @param containsTable 是否包含表格
 * @param containsCode 是否包含代码块
 * @param containsList 是否包含列表
 * @param parentChunkId 父级分块编号
 * @param parentCharStart 父级上下文起始字符位置
 * @param parentCharEnd 父级上下文结束字符位置
 * @author data-agent
 */
public record ChunkMetadata(String sectionPath,
        int charStart,
        int charEnd,
        boolean containsTable,
        boolean containsCode,
        boolean containsList,
        String parentChunkId,
        int parentCharStart,
        int parentCharEnd) {

    public ChunkMetadata(String sectionPath, int charStart, int charEnd, boolean containsTable,
            boolean containsCode, boolean containsList) {
        this(sectionPath, charStart, charEnd, containsTable, containsCode, containsList, "", 0, 0);
    }

    public static ChunkMetadata plain(String text) {
        return new ChunkMetadata("", 0, Math.max(0, text == null ? 0 : text.length()),
                false, false, false);
    }

    public ChunkMetadata withParent(String newParentChunkId, int newParentCharStart, int newParentCharEnd) {
        return new ChunkMetadata(sectionPath, charStart, charEnd, containsTable, containsCode, containsList,
                newParentChunkId, newParentCharStart, newParentCharEnd);
    }

    public boolean hasParent() {
        return !hasText(parentChunkId) || parentCharEnd <= parentCharStart;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
