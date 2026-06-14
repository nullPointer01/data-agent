package com.ai.service.file;

import com.ai.service.AuditLogService;

import com.ai.model.FileMetadata;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

/**
 * 将文件提交到异步工作者并一致地处理执行器反压。
 *
 * @author data-agent
 */
@Service
public class FileProcessingSubmissionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileProcessingSubmissionService.class);
    private static final String QUEUE_FULL_MESSAGE = "文件处理队列已满，请稍后重试";
    private static final String METRIC_FILE_PROCESSING_TOTAL = "data_agent_file_processing_total";
    private static final String TAG_STATUS = "status";
    private static final String STATUS_REJECTED = "rejected";
    private static final String RESOURCE_TYPE_FILE = "FILE";
    private static final String ACTION_PROCESSING_REJECTED = "FILE_PROCESSING_REJECTED";
    private static final String AUDIT_STATUS_FAILED = "FAILED";

    private final FileProcessingWorker fileProcessingWorker;
    private final FileMetadataLifecycleService metadataLifecycleService;
    private final MeterRegistry meterRegistry;
    private final AuditLogService auditLogService;

    public FileProcessingSubmissionService(FileProcessingWorker fileProcessingWorker,
            FileMetadataLifecycleService metadataLifecycleService,
            MeterRegistry meterRegistry,
            AuditLogService auditLogService) {
        this.fileProcessingWorker = fileProcessingWorker;
        this.metadataLifecycleService = metadataLifecycleService;
        this.meterRegistry = meterRegistry;
        this.auditLogService = auditLogService;
    }

    /**
     * Submits a file to the async worker. Queue rejection is converted to a persisted FAILED state.
     *
     * @param metadata uploaded file metadata
     * @return submission result
     */
    public FileProcessingSubmissionResult submit(FileMetadata metadata) {
        try {
            fileProcessingWorker.processAsync(metadata.getFileId());
            return FileProcessingSubmissionResult.acceptedResult();
        } catch (TaskRejectedException e) {
            metadataLifecycleService.markRejected(metadata.getFileId(), QUEUE_FULL_MESSAGE);
            meterRegistry.counter(METRIC_FILE_PROCESSING_TOTAL, TAG_STATUS, STATUS_REJECTED).increment();
            auditLogService.record(ACTION_PROCESSING_REJECTED, RESOURCE_TYPE_FILE, metadata.getFileId(),
                    AUDIT_STATUS_FAILED, "文件处理队列已满: " + metadata.getFilename());
            LOGGER.warn("File processing queue rejected upload: {}", metadata.getFileId());
            return FileProcessingSubmissionResult.rejectedResult(QUEUE_FULL_MESSAGE);
        }
    }
}
