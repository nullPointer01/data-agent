package com.ai.service.file;

import com.ai.service.AuditLogService;

import com.ai.file.dto.FileListResponse;
import com.ai.file.dto.FileMutationResponse;
import com.ai.file.dto.FileResponse;
import com.ai.model.FileMetadata;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default file upload, async processing, and vector cleanup service.
 *
 * @author data-agent
 */
@Service
public class FileProcessingServiceImpl implements FileProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileProcessingServiceImpl.class);
    private static final String METRIC_FILE_UPLOAD_TOTAL = "data_agent_file_upload_total";
    private static final String METRIC_FILE_DELETE_TOTAL = "data_agent_file_delete_total";
    private static final String TAG_STATUS = "status";
    private static final String STATUS_ACCEPTED = "accepted";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_SUCCESS = "success";
    private static final String RESOURCE_TYPE_FILE = "FILE";
    private static final String AUDIT_STATUS_SUCCESS = "SUCCESS";
    private static final String AUDIT_STATUS_FAILED = "FAILED";
    private static final String ACTION_UPLOAD_ACCEPTED = "FILE_UPLOAD_ACCEPTED";
    private static final String ACTION_UPLOAD_FAILED = "FILE_UPLOAD_FAILED";
    private static final String ACTION_FILE_DELETE = "FILE_DELETE";

    private final FileStorageService fileStorageService;
    private final FileMetadataLifecycleService metadataLifecycleService;
    private final FileProcessingSubmissionService submissionService;
    private final FileVectorCleanupService vectorCleanupService;
    private final MeterRegistry meterRegistry;
    private final AuditLogService auditLogService;

    public FileProcessingServiceImpl(FileStorageService fileStorageService,
            FileMetadataLifecycleService metadataLifecycleService,
            FileProcessingSubmissionService submissionService,
            FileVectorCleanupService vectorCleanupService,
            MeterRegistry meterRegistry,
            AuditLogService auditLogService) {
        this.fileStorageService = fileStorageService;
        this.metadataLifecycleService = metadataLifecycleService;
        this.submissionService = submissionService;
        this.vectorCleanupService = vectorCleanupService;
        this.meterRegistry = meterRegistry;
        this.auditLogService = auditLogService;
    }

    @Override
    public FileResponse processFile(MultipartFile file) {
        FileStorageObject storedFile = null;
        FileMetadata metadata = null;
        try {
            String fileId = UUID.randomUUID().toString();
            storedFile = fileStorageService.store(fileId, file);
            metadata = metadataLifecycleService.createQueued(storedFile);
            meterRegistry.counter(METRIC_FILE_UPLOAD_TOTAL, TAG_STATUS, STATUS_ACCEPTED).increment();
            auditLogService.record(ACTION_UPLOAD_ACCEPTED, RESOURCE_TYPE_FILE, fileId, AUDIT_STATUS_SUCCESS,
                    "文件已上传并进入后台处理: " + storedFile.filename());
            LOGGER.info("File upload accepted: {}, tenant: {}", storedFile.filename(), metadata.getTenantId());

            FileProcessingSubmissionResult submission = submissionService.submit(metadata);
            if (!submission.accepted()) {
                return FileResponse.failure(fileId, submission.message());
            }

            return FileResponse.accepted(metadata, "文件已上传，正在后台处理");
        } catch (Exception e) {
            cleanupUntrackedFile(storedFile, metadata);
            LOGGER.error("File upload failed", e);
            meterRegistry.counter(METRIC_FILE_UPLOAD_TOTAL, TAG_STATUS, STATUS_FAILED).increment();
            auditLogService.record(ACTION_UPLOAD_FAILED, RESOURCE_TYPE_FILE, null, AUDIT_STATUS_FAILED, e.getMessage());
            return FileResponse.failure("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public FileListResponse listFiles() {
        List<FileMetadata> files = metadataLifecycleService.listCurrentTenant();
        return new FileListResponse(true, files.stream()
                .map(FileResponse::from)
                .collect(Collectors.toList()));
    }

    @Override
    public FileMutationResponse deleteFile(String fileId) {
        Optional<FileMetadata> target = metadataLifecycleService.findCurrentTenantFile(fileId);

        if (target.isEmpty()) {
            return FileMutationResponse.failure("文件不存在或无权限");
        }

        FileMetadata metadata = target.get();
        try {
            fileStorageService.delete(metadata.getPath());
            metadataLifecycleService.delete(metadata);
            vectorCleanupService.removeFileVectors(fileId, metadata.getTenantId());
            meterRegistry.counter(METRIC_FILE_DELETE_TOTAL, TAG_STATUS, STATUS_SUCCESS).increment();
            auditLogService.record(ACTION_FILE_DELETE, RESOURCE_TYPE_FILE, fileId, AUDIT_STATUS_SUCCESS,
                    "文件删除成功: " + metadata.getFilename());
            return FileMutationResponse.deleted();
        } catch (Exception e) {
            LOGGER.error("File deletion failed", e);
            meterRegistry.counter(METRIC_FILE_DELETE_TOTAL, TAG_STATUS, STATUS_FAILED).increment();
            auditLogService.record(ACTION_FILE_DELETE, RESOURCE_TYPE_FILE, fileId, AUDIT_STATUS_FAILED,
                    e.getMessage());
            return FileMutationResponse.failure("文件删除失败: " + e.getMessage());
        }
    }

    @Override
    public String getFileContent(String fileId) {
        return metadataLifecycleService.findCurrentTenantFile(fileId)
                .map(FileMetadata::getContent)
                .orElse(null);
    }

    @Override
    public FileResponse getFileInfo(String fileId) {
        return metadataLifecycleService.findCurrentTenantFile(fileId)
                .map(FileResponse::from)
                .orElse(FileResponse.failure("文件不存在或无权限"));
    }

    private void cleanupUntrackedFile(FileStorageObject storedFile, FileMetadata metadata) {
        if (storedFile == null || metadata != null) {
            return;
        }
        try {
            fileStorageService.delete(storedFile.path());
        } catch (IOException | SecurityException cleanupException) {
            LOGGER.warn("Failed to cleanup uploaded file after metadata creation failure: {}", storedFile.fileId(),
                    cleanupException);
        }
    }
}
