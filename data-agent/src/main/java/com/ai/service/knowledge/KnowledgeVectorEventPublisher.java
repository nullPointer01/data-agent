package com.ai.service.knowledge;

import com.ai.model.KnowledgeEntry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 发布知识向量同步事件。
 *
 * @author data-agent
 */
@Component
public class KnowledgeVectorEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public KnowledgeVectorEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishIndex(KnowledgeEntry entry) {
        applicationEventPublisher.publishEvent(KnowledgeVectorEvent.index(
                entry.getKnowledgeId(), entry.getTenantId(), entry.getCreatedBy(), entry.getName(),
                entry.getContent()));
    }

    public void publishReindex(KnowledgeEntry entry) {
        applicationEventPublisher.publishEvent(KnowledgeVectorEvent.reindex(
                entry.getKnowledgeId(), entry.getTenantId(), entry.getCreatedBy(), entry.getName(),
                entry.getContent()));
    }

    public void publishDelete(KnowledgeEntry entry) {
        applicationEventPublisher.publishEvent(KnowledgeVectorEvent.delete(entry.getKnowledgeId(), entry.getTenantId()));
    }
}
