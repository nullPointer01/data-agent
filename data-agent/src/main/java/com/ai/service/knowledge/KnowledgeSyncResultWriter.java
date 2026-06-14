package com.ai.service.knowledge;

import com.ai.model.KnowledgeEntry;
import com.ai.model.KnowledgeSyncConfig;
import com.ai.repository.KnowledgeEntryRepository;
import com.ai.repository.KnowledgeSyncConfigRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;

/**
 * 持久化获取的知识内容并以事务方式更新同步状态。
 *
 * @author data-agent
 */
@Component
public class KnowledgeSyncResultWriter {

    private final KnowledgeSyncConfigRepository syncConfigRepository;
    private final KnowledgeEntryRepository knowledgeEntryRepository;
    private final KnowledgeVectorIndexService knowledgeVectorIndexService;
    private final KnowledgeVectorEventPublisher knowledgeVectorEventPublisher;
    private final KnowledgeSyncSecurityService securityService;
    private final TransactionTemplate transactionTemplate;

    public KnowledgeSyncResultWriter(KnowledgeSyncConfigRepository syncConfigRepository,
            KnowledgeEntryRepository knowledgeEntryRepository,
            KnowledgeVectorIndexService knowledgeVectorIndexService,
            KnowledgeVectorEventPublisher knowledgeVectorEventPublisher,
            KnowledgeSyncSecurityService securityService,
            TransactionTemplate transactionTemplate) {
        this.syncConfigRepository = syncConfigRepository;
        this.knowledgeEntryRepository = knowledgeEntryRepository;
        this.knowledgeVectorIndexService = knowledgeVectorIndexService;
        this.knowledgeVectorEventPublisher = knowledgeVectorEventPublisher;
        this.securityService = securityService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Saves synchronization result and schedules vector reindex after transaction commit.
     *
     * @param sourceConfig source sync config snapshot
     * @param content fetched content
     * @return user-facing success message
     */
    public String saveSyncResult(KnowledgeSyncConfig sourceConfig, String content) {
        return transactionTemplate.execute(status -> {
            KnowledgeSyncConfig config = securityService.findTenantSyncConfig(
                    sourceConfig.getKnowledgeId(), sourceConfig.getTenantId());
            KnowledgeEntry entry = securityService.assertTenantKnowledge(
                    sourceConfig.getKnowledgeId(), sourceConfig.getTenantId());

            updateKnowledgeEntry(entry, content);
            updateSyncConfig(config);
            knowledgeVectorEventPublisher.publishReindex(entry);
            return "同步成功，向量索引任务已提交，内容长度: " + content.length() + " 字符";
        });
    }

    private void updateKnowledgeEntry(KnowledgeEntry entry, String content) {
        entry.setContent(content);
        entry.setContentLength(content.length());
        int chunkCount = knowledgeVectorIndexService.estimateChunkCount(content);
        entry.setChunkCount(chunkCount);
        knowledgeEntryRepository.saveAndFlush(entry);
    }

    private void updateSyncConfig(KnowledgeSyncConfig config) {
        config.setLastSyncAt(new Date());
        config.setLastSyncStatus("成功");
        syncConfigRepository.save(config);
    }
}
