package com.ai.rag.fulltext;

import com.ai.vector.ChunkMetadata;

/**
 * 全文索引文档。
 *
 * @param sourceType 来源类型
 * @param sourceId 来源编号
 * @param chunkId 分块编号
 * @param tenantId 租户编号
 * @param userId 用户编号
 * @param title 标题
 * @param content 分块内容
 * @param metadata 分块元数据
 * @param parentContext 父级上下文
 * @author data-agent
 */
public record FullTextDocument(String sourceType,
        String sourceId,
        String chunkId,
        String tenantId,
        String userId,
        String title,
        String content,
        ChunkMetadata metadata,
        String parentContext) {

    public FullTextDocument(String sourceType, String sourceId, String chunkId, String tenantId, String userId,
            String title, String content, ChunkMetadata metadata) {
        this(sourceType, sourceId, chunkId, tenantId, userId, title, content, metadata, "");
    }
}
