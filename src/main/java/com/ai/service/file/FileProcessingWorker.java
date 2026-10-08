package com.ai.service.file;

import com.ai.service.AuditLogService;

import com.ai.model.FileMetadata;
import com.ai.rag.indexing.RagDocumentIndexer;
import com.ai.vector.VectorDocumentTypes;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * 异步文件解析和向量索引工作者。
 *
 * @author data-agent
 */
@Service
public class FileProcessingWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileProcessingWorker.class);

    private final FileMetadataLifecycleService metadataLifecycleService;
    private final RagDocumentIndexer documentIndexer;
    private final FileParserService fileParserService;
    private final MeterRegistry meterRegistry;
    private final AuditLogService auditLogService;

    public FileProcessingWorker(FileMetadataLifecycleService metadataLifecycleService,
            RagDocumentIndexer documentIndexer,
            FileParserService fileParserService,
            MeterRegistry meterRegistry,
            AuditLogService auditLogService) {
        this.metadataLifecycleService = metadataLifecycleService;
        this.documentIndexer = documentIndexer;
        this.fileParserService = fileParserService;
        this.meterRegistry = meterRegistry;
        this.auditLogService = auditLogService;
    }

    /** 持久任务工作者同步调用；不另起异步线程，异常必须回到租约与重试状态。 */
    public void process(String fileId, String tenant, String user) throws Exception {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            parseAndIndex(fileId, tenant, user);
            meterRegistry.counter("data_agent_file_processing_total", "status", "success").increment();
        } catch (Exception e) {
            meterRegistry.counter("data_agent_file_processing_total", "status", "failed").increment();
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("data_agent_file_processing_duration"));
        }
    }

    private void parseAndIndex(String fileId, String tenant, String user) throws Exception {
        FileMetadata metadata = metadataLifecycleService.markProcessing(fileId, tenant, user);

        try {
            String content = fileParserService.parse(Path.of(metadata.getPath()), metadata.getFilename(),
                    metadata.getContentType());

            documentIndexer.replace(VectorDocumentTypes.FILE, fileId, metadata.getFilename(),
                    fileParserService.isIndexableFileContent(content) ? content : "",
                    metadata.getTenantId(), metadata.getUploadedBy());

            metadataLifecycleService.markCompleted(fileId, tenant, user, content);
            auditLogService.record("FILE_PROCESSING_COMPLETED", "FILE", fileId, "SUCCESS",
                    "文件处理完成: " + metadata.getFilename(),
                    metadata.getTenantId(), metadata.getUploadedBy(), null, null);
            LOGGER.info("File processed: {}, tenant: {}", metadata.getFilename(), metadata.getTenantId());
        } catch (Exception e) {
            metadataLifecycleService.markFailed(fileId, tenant, user, e.getMessage());
            auditLogService.record("FILE_PROCESSING_FAILED", "FILE", fileId, "FAILED",
                    "文件处理失败: " + e.getMessage(),
                    metadata.getTenantId(), metadata.getUploadedBy(), null, null);
            throw e;
        }
    }

}
