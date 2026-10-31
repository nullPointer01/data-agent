package com.ai.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


/** 按小时清理已删除会话；索引定位、会话/行数双重限额，不扫描正常历史。 */
@Component
public class ConversationRetentionScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(ConversationRetentionScheduler.class);
    private final ConversationLifecycleMapper mapper;
    private final ConversationLifecycleService lifecycle;
    private final int retentionDays;
    private final int sessionBatch;
    private final int rowBatch;
    private final boolean enabled;
    private final com.ai.agent.backgroundrun.BackgroundRunMapper backgroundRuns;
    private final com.ai.agent.backgroundrun.BackgroundRunStore backgroundStore;

    public ConversationRetentionScheduler(ConversationLifecycleMapper mapper, ConversationLifecycleService lifecycle,
            @Value("${app.conversation.retention.enabled:true}") boolean enabled,
            @Value("${app.conversation.retention.deleted-days:30}") int retentionDays,
            @Value("${app.conversation.retention.session-batch:10}") int sessionBatch,
            @Value("${app.conversation.retention.row-batch:200}") int rowBatch,
            com.ai.agent.backgroundrun.BackgroundRunMapper backgroundRuns,
            com.ai.agent.backgroundrun.BackgroundRunStore backgroundStore) {
        if (retentionDays < 1 || sessionBatch < 1 || sessionBatch > 100 || rowBatch < 1 || rowBatch > 1000) {
            throw new IllegalArgumentException("会话保留期和清理批次配置无效");
        }
        this.mapper = mapper;
        this.lifecycle = lifecycle;
        this.enabled = enabled;
        this.retentionDays = retentionDays;
        this.sessionBatch = sessionBatch;
        this.rowBatch = rowBatch;
        this.backgroundRuns = backgroundRuns;
        this.backgroundStore = backgroundStore;
    }

    @Scheduled(cron = "${app.conversation.retention.cron:0 17 * * * *}")
    public void purge() {
        if (!enabled) return;
        for (String runId : backgroundRuns.expiredLogs()) {
            try { backgroundStore.purgeEventLog(runId, rowBatch); }
            catch (RuntimeException failure) { LOG.warn("运行事件到期清理暂未完成: runId={}", runId); }
        }
        for (String sessionId : mapper.expired(retentionDays, sessionBatch)) {
            try {
                lifecycle.purgeOne(sessionId, retentionDays, rowBatch);
            } catch (RuntimeException error) {
                LOG.warn("会话到期清理失败: sessionId={}", sessionId, error);
            }
        }
    }
}
