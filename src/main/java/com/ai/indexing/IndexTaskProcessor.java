package com.ai.indexing;

import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryEntry;
import com.ai.rag.indexing.RagDocumentIndexer;
import com.ai.repository.ConversationSessionRepository;
import com.ai.repository.FileMetadataRepository;
import com.ai.repository.KnowledgeEntryRepository;
import com.ai.repository.MemoryEntryRepository;
import com.ai.service.VectorMemoryService;
import com.ai.service.file.FileProcessingWorker;
import com.ai.service.knowledge.KnowledgeVectorIndexService;
import com.ai.vector.VectorDocumentTypes;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 按持久任务身份读取最新来源；租约/重试由 Worker 管理，不能依赖登录线程上下文。 */
@Component
public class IndexTaskProcessor {
    private final IndexTaskStore store;
    private final KnowledgeEntryRepository knowledge;
    private final FileMetadataRepository files;
    private final MemoryEntryRepository memories;
    private final ConversationSessionRepository sessions;
    private final KnowledgeVectorIndexService indexes;
    private final RagDocumentIndexer documents;
    private final FileProcessingWorker fileWorker;
    private final VectorMemoryService vectors;

    public IndexTaskProcessor(IndexTaskStore store, KnowledgeEntryRepository knowledge, FileMetadataRepository files,
            MemoryEntryRepository memories, ConversationSessionRepository sessions, KnowledgeVectorIndexService indexes,
            RagDocumentIndexer documents, FileProcessingWorker fileWorker, VectorMemoryService vectors) {
        this.store = store;
        this.knowledge = knowledge;
        this.files = files;
        this.memories = memories;
        this.sessions = sessions;
        this.indexes = indexes;
        this.documents = documents;
        this.fileWorker = fileWorker;
        this.vectors = vectors;
    }

    /** 最新主数据决定写入或删除；任何外部失败保留失败语义返回租约工作者。 */
    public void process(IndexTask task) throws Exception {
        switch (task.kind()) {
            case KNOWLEDGE -> {
                var entry = knowledge.findByKnowledgeIdAndTenantIdAndCreatedBy(
                        task.sourceId(), task.tenantId(), task.userId());
                if (entry.isPresent()) indexes.reindex(entry.get());
                else indexes.remove(task.sourceId(), task.tenantId());
            }
            case FILE -> {
                var file = files.findByFileIdAndTenantIdAndUploadedBy(task.sourceId(), task.tenantId(), task.userId());
                if (file.isPresent()) fileWorker.process(task.sourceId(), task.tenantId(), task.userId());
                else documents.remove(VectorDocumentTypes.FILE, task.sourceId(), task.tenantId());
            }
            case MEMORY -> {
                var memory = memories.findByMemoryIdAndTenantIdAndUserId(task.sourceId(), task.tenantId(), task.userId())
                        .filter(entry -> entry.getTier() == MemoryTier.LONG_TERM)
                        .filter(entry -> entry.getExpiresAt() == null || entry.getExpiresAt().isAfter(LocalDateTime.now()))
                        .filter(entry -> memoryContent(entry) != null && !memoryContent(entry).isBlank());
                if (memory.isPresent()) vectors.indexMemory(task.sourceId(), memoryContent(memory.get()), task.tenantId(), task.userId());
                else vectors.removeFromStore(VectorDocumentTypes.MEMORY, task.sourceId(), task.tenantId());
            }
            case CONVERSATION -> {
                var payload = store.conversation(task);
                if (sessions.findBySessionIdAndUserIdAndTenantId(payload.sessionId(), task.userId(), task.tenantId()).isPresent()) {
                    vectors.indexConversationTurn(task.sourceId(), payload.question(), payload.answer(), task.tenantId(), task.userId());
                }
            }
        }
    }

    /** 与长期记忆正常写入保持相同正文合同，重建不能把压缩正文悄悄换回原文。 */
    private static String memoryContent(MemoryEntry entry) {
        return entry.getCompressedContent() != null && !entry.getCompressedContent().isBlank()
                ? entry.getCompressedContent() : entry.getContent();
    }
}
