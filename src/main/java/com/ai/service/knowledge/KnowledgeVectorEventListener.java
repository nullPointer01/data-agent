package com.ai.service.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 处理数据库事务提交后的知识向量事件。
 *
 * @author data-agent
 */
@Component
public class KnowledgeVectorEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeVectorEventListener.class);

    private final KnowledgeVectorIndexService knowledgeVectorIndexService;

    public KnowledgeVectorEventListener(KnowledgeVectorIndexService knowledgeVectorIndexService) {
        this.knowledgeVectorIndexService = knowledgeVectorIndexService;
    }

    @Async("knowledgeVectorExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(KnowledgeVectorEvent event) {
        try {
            switch (event.type()) {
                case INDEX:
                    knowledgeVectorIndexService.index(event);
                    break;
                case REINDEX:
                    knowledgeVectorIndexService.reindex(event);
                    break;
                case DELETE:
                    knowledgeVectorIndexService.remove(event.knowledgeId(), event.tenantId());
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported knowledge vector event: " + event.type());
            }
        } catch (Exception e) {
            LOGGER.error("Knowledge vector event failed: type={}, knowledgeId={}",
                    event.type(), event.knowledgeId(), e);
        }
    }
}
