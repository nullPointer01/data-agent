package com.ai.service.knowledge;

/**
 * 知识向量事件类型。
 *
 * @author data-agent
 */
public enum KnowledgeVectorEventType {

    /**
     * Index a newly created knowledge entry.
     */
    INDEX,

    /**
     * Remove old vectors and index the latest knowledge entry content.
     */
    REINDEX,

    /**
     * Remove vectors for a deleted knowledge entry.
     */
    DELETE
}
