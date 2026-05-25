package com.ai.service.knowledge;

/**
 * Knowledge vector event types.
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
