package com.ai.vector;

/**
 * 文本分块元数据。
 *
 * @param sectionPath 标题路径
 * @param charStart 起始字符位置
 * @param charEnd 结束字符位置
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

    public ChunkMetadata withRange(int newCharStart, int newCharEnd) {
        return new ChunkMetadata(sectionPath, newCharStart, newCharEnd, containsTable, containsCode, containsList,
                parentChunkId, parentCharStart, parentCharEnd);
    }

    public ChunkMetadata withParent(String newParentChunkId, int newParentCharStart, int newParentCharEnd) {
        return new ChunkMetadata(sectionPath, charStart, charEnd, containsTable, containsCode, containsList,
                newParentChunkId, newParentCharStart, newParentCharEnd);
    }

    public ChunkMetadata merge(ChunkMetadata next) {
        String mergedSectionPath = sectionPath == null || sectionPath.isBlank() ? next.sectionPath : sectionPath;
        String mergedParentChunkId = hasText(parentChunkId) ? parentChunkId : next.parentChunkId;
        int mergedParentStart = mergeParentStart(parentCharStart, next.parentCharStart);
        int mergedParentEnd = Math.max(parentCharEnd, next.parentCharEnd);
        return new ChunkMetadata(mergedSectionPath, Math.min(charStart, next.charStart),
                Math.max(charEnd, next.charEnd), containsTable || next.containsTable,
                containsCode || next.containsCode, containsList || next.containsList,
                mergedParentChunkId, mergedParentStart, mergedParentEnd);
    }

    public boolean hasParent() {
        return hasText(parentChunkId) && parentCharEnd > parentCharStart;
    }

    private int mergeParentStart(int currentParentStart, int nextParentStart) {
        if (currentParentStart <= 0) {
            return nextParentStart;
        }
        if (nextParentStart <= 0) {
            return currentParentStart;
        }
        return Math.min(currentParentStart, nextParentStart);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
