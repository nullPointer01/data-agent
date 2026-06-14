package com.ai.service.knowledge;

import com.ai.exception.KnowledgeSyncException;
import com.ai.knowledge.dto.KnowledgeMutationResponse;
import com.ai.knowledge.dto.KnowledgeSyncConfigEnvelope;
import com.ai.knowledge.dto.KnowledgeSyncConfigRequest;
import com.ai.knowledge.dto.KnowledgeSyncConfigResponse;
import com.ai.model.KnowledgeSyncConfig;
import com.ai.repository.KnowledgeSyncConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 将外部知识源同步到知识条目和向量存储。
 *
 * @author data-agent
 */
@Service
public class KnowledgeSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeSyncService.class);
    private static final long SCHEDULE_FIXED_DELAY_MS = 3_600_000L;
    private static final int ERROR_STATUS_MAX_LENGTH = 80;

    private final KnowledgeSyncConfigRepository syncConfigRepository;
    private final KnowledgeSyncPolicy syncPolicy;
    private final KnowledgeSyncSecurityService securityService;
    private final KnowledgeSyncContentFetcher contentFetcher;
    private final KnowledgeSyncResultWriter resultWriter;

    public KnowledgeSyncService(KnowledgeSyncConfigRepository syncConfigRepository, KnowledgeSyncPolicy syncPolicy,
            KnowledgeSyncSecurityService securityService, KnowledgeSyncContentFetcher contentFetcher,
            KnowledgeSyncResultWriter resultWriter) {
        this.syncConfigRepository = syncConfigRepository;
        this.syncPolicy = syncPolicy;
        this.securityService = securityService;
        this.contentFetcher = contentFetcher;
        this.resultWriter = resultWriter;
    }

    /**
     * Scan enabled automatic sync configs every hour.
     */
    @Scheduled(fixedDelay = SCHEDULE_FIXED_DELAY_MS)
    public void scheduledSync() {
        List<KnowledgeSyncConfig> configs = syncConfigRepository.findByEnabledTrue();
        if (configs.isEmpty()) {
            return;
        }
        LOGGER.info("KnowledgeSyncService: checking {} sync configs", configs.size());
        for (KnowledgeSyncConfig config : configs) {
            try {
                doSync(config);
            } catch (KnowledgeSyncException | IllegalArgumentException | SecurityException e) {
                LOGGER.error("Sync failed for knowledgeId={}: {}", config.getKnowledgeId(), e.getMessage());
                config.setLastSyncStatus("失败: " + syncPolicy.truncate(e.getMessage(), ERROR_STATUS_MAX_LENGTH));
                config.setLastSyncAt(new Date());
                syncConfigRepository.save(config);
            }
        }
    }

    public KnowledgeSyncConfigEnvelope getSyncConfig(String knowledgeId) {
        String tenantId = securityService.currentTenantIdOrThrow();
        securityService.assertCurrentTenantKnowledge(knowledgeId);
        return syncConfigRepository.findByKnowledgeIdAndTenantId(knowledgeId, tenantId)
                .map(KnowledgeSyncConfigResponse::from)
                .map(KnowledgeSyncConfigEnvelope::success)
                .orElseGet(() -> KnowledgeSyncConfigEnvelope.success(null));
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeMutationResponse saveSyncConfig(String knowledgeId, KnowledgeSyncConfigRequest request) {
        String tenantId = securityService.currentTenantIdOrThrow();
        securityService.assertCurrentTenantKnowledge(knowledgeId);
        KnowledgeSyncConfig config = syncConfigRepository.findByKnowledgeIdAndTenantId(knowledgeId, tenantId)
                .orElseGet(KnowledgeSyncConfig::new);
        config.setKnowledgeId(knowledgeId);
        config.setSyncType(syncPolicy.resolveSyncType(request.syncType()));
        config.setSourceUrl(request.sourceUrl());
        config.setCronExpression(syncPolicy.resolveCronExpression(request.cronExpression()));
        config.setEnabled(request.enabled() == null || request.enabled());
        config.setTenantId(tenantId);
        syncConfigRepository.save(config);
        return KnowledgeMutationResponse.success("自动同步配置已保存");
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeMutationResponse deleteSyncConfig(String knowledgeId) {
        String tenantId = securityService.currentTenantIdOrThrow();
        securityService.assertCurrentTenantKnowledge(knowledgeId);
        syncConfigRepository.findByKnowledgeIdAndTenantId(knowledgeId, tenantId)
                .ifPresent(syncConfigRepository::delete);
        return KnowledgeMutationResponse.success("自动同步配置已删除");
    }

    public KnowledgeMutationResponse triggerSync(String knowledgeId) {
        securityService.assertCurrentTenantKnowledge(knowledgeId);
        KnowledgeSyncConfig config = securityService.findOwnedSyncConfig(knowledgeId);
        try {
            return KnowledgeMutationResponse.success(doSync(config));
        } catch (KnowledgeSyncException | IllegalArgumentException | SecurityException e) {
            LOGGER.warn("Manual sync failed for knowledgeId={}: {}", knowledgeId, e.getMessage());
            return KnowledgeMutationResponse.failure("同步失败: " + e.getMessage());
        }
    }

    private String doSync(KnowledgeSyncConfig config) {
        String content = contentFetcher.fetch(config.getSyncType(), config.getSourceUrl());
        if (content == null || content.isBlank()) {
            throw new KnowledgeSyncException("获取内容为空");
        }
        LOGGER.info("Sync success and reindexing scheduled: knowledgeId={}, contentLength={}",
                config.getKnowledgeId(), content.length());
        return resultWriter.saveSyncResult(config, content);
    }
}
