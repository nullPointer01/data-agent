package com.ai.service.knowledge;

/**
 * 用于在事务提交后将知识条目同步到向量存储的事件。
 *
 * @author data-agent
 */
public record KnowledgeVectorEvent(
        KnowledgeVectorEventType type,
        String knowledgeId,
        String tenantId,
        String userId,
        String title,
        String content) {

    public static KnowledgeVectorEvent index(String knowledgeId, String tenantId, String userId, String title,
            String content) {
        return new KnowledgeVectorEvent(KnowledgeVectorEventType.INDEX, knowledgeId, tenantId, userId, title, content);
    }

    public static KnowledgeVectorEvent reindex(String knowledgeId, String tenantId, String userId, String title,
            String content) {
        return new KnowledgeVectorEvent(KnowledgeVectorEventType.REINDEX, knowledgeId, tenantId, userId, title,
                content);
    }

    public static KnowledgeVectorEvent delete(String knowledgeId, String tenantId) {
        return new KnowledgeVectorEvent(KnowledgeVectorEventType.DELETE, knowledgeId, tenantId, null, null, null);
    }
}
