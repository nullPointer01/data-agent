package com.ai.service.file;

import com.ai.service.AuditLogService;

import com.ai.service.VectorMemoryService;

import com.ai.model.FileMetadata;
import com.ai.rag.fulltext.FullTextDocument;
import com.ai.rag.fulltext.FullTextIndexService;
import com.ai.vector.TextChunker;
import com.ai.vector.VectorChunk;
import com.ai.vector.VectorDocumentTypes;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * 异步文件解析和向量索引工作者。
 *
 * @author data-agent
 */
@Service
public class FileProcessingWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileProcessingWorker.class);

    private final FileMetadataLifecycleService metadataLifecycleService;
    private final ApplicationEventPublisher eventPublisher;
    private final VectorMemoryService vectorMemoryService;
    private final TextChunker textChunker;
    private final FullTextIndexService fullTextIndexService;
    private final FileParserService fileParserService;
    private final MeterRegistry meterRegistry;
    private final AuditLogService auditLogService;

    public FileProcessingWorker(FileMetadataLifecycleService metadataLifecycleService,
            ApplicationEventPublisher eventPublisher,
            VectorMemoryService vectorMemoryService,
            TextChunker textChunker,
            FullTextIndexService fullTextIndexService,
            FileParserService fileParserService,
            MeterRegistry meterRegistry,
            AuditLogService auditLogService) {
        this.metadataLifecycleService = metadataLifecycleService;
        this.eventPublisher = eventPublisher;
        this.vectorMemoryService = vectorMemoryService;
        this.textChunker = textChunker;
        this.fullTextIndexService = fullTextIndexService;
        this.fileParserService = fileParserService;
        this.meterRegistry = meterRegistry;
        this.auditLogService = auditLogService;
    }

    @Async("fileProcessingExecutor")
    public void processAsync(String fileId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            process(fileId);
            meterRegistry.counter("data_agent_file_processing_total", "status", "success").increment();
        } catch (Exception e) {
            meterRegistry.counter("data_agent_file_processing_total", "status", "failed").increment();
            LOGGER.error("Async file processing failed: {}", fileId, e);
        } finally {
            sample.stop(meterRegistry.timer("data_agent_file_processing_duration"));
        }
    }

    void process(String fileId) throws Exception {
        FileMetadata metadata = metadataLifecycleService.markProcessing(fileId);

        try {
            String content = fileParserService.parse(Path.of(metadata.getPath()), metadata.getFilename(),
                    metadata.getContentType());

            if (fileParserService.isIndexableFileContent(content)) {
                eventPublisher.publishEvent(new FileUploadedEvent(this, fileId, metadata.getFilename(), content));
                vectorMemoryService.indexFile(fileId, metadata.getFilename(), content,
                        metadata.getTenantId(), metadata.getUploadedBy());
                indexFullText(metadata, content);
            }

            metadataLifecycleService.markCompleted(fileId, content);
            auditLogService.record("FILE_PROCESSING_COMPLETED", "FILE", fileId, "SUCCESS",
                    "文件处理完成: " + metadata.getFilename(),
                    metadata.getTenantId(), metadata.getUploadedBy(), null, null);
            LOGGER.info("File processed: {}, tenant: {}", metadata.getFilename(), metadata.getTenantId());
        } catch (Exception e) {
            metadataLifecycleService.markFailed(fileId, e.getMessage());
            auditLogService.record("FILE_PROCESSING_FAILED", "FILE", fileId, "FAILED",
                    "文件处理失败: " + e.getMessage(),
                    metadata.getTenantId(), metadata.getUploadedBy(), null, null);
            throw e;
        }
    }

    private void indexFullText(FileMetadata metadata, String content) {
        List<FullTextDocument> documents = textChunker.split(metadata.getFileId(), content).stream()
                .map(chunk -> toFullTextDocument(metadata, chunk, content))
                .toList();
        fullTextIndexService.index(documents);
    }

    private FullTextDocument toFullTextDocument(FileMetadata metadata, VectorChunk chunk, String sourceContent) {
        return new FullTextDocument(VectorDocumentTypes.FILE, metadata.getFileId(), chunk.id(),
                metadata.getTenantId(), metadata.getUploadedBy(), metadata.getFilename(), chunk.text(),
                chunk.metadata(), parentContext(chunk, sourceContent));
    }

    private String parentContext(VectorChunk chunk, String sourceContent) {
        if (!chunk.metadata().hasParent() || sourceContent == null || sourceContent.isBlank()) {
            return "";
        }
        int start = Math.max(0, Math.min(chunk.parentCharStart(), sourceContent.length()));
        int end = Math.max(start, Math.min(chunk.parentCharEnd(), sourceContent.length()));
        return sourceContent.substring(start, end).trim();
    }
}
