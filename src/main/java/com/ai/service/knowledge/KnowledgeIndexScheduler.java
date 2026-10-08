package com.ai.service.knowledge;

import com.ai.indexing.IndexTask;
import com.ai.indexing.IndexTaskStore;
import com.ai.model.KnowledgeEntry;
import org.springframework.stereotype.Component;

/** 知识增删改与索引任务同事务提交；任务只保存身份，执行时读取最新主库或删除残留索引。 */
@Component
public class KnowledgeIndexScheduler {
    private final IndexTaskStore tasks;
    public KnowledgeIndexScheduler(IndexTaskStore tasks) {
        this.tasks = tasks;
    }

    public void schedule(KnowledgeEntry entry) {
        tasks.enqueue(IndexTask.Kind.KNOWLEDGE, entry.getTenantId(), entry.getCreatedBy(), entry.getKnowledgeId(), null);
    }
}
