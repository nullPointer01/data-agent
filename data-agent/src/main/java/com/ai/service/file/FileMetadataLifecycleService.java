package com.ai.service.file;

import com.ai.model.FileMetadata;
import com.ai.model.FileProcessingStatus;
import com.ai.repository.FileMetadataRepository;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Owns tenant-scoped file metadata state transitions.
 *
 * @author data-agent
 */
@Service
public class FileMetadataLifecycleService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final FileMetadataRepository fileMetadataRepository;
    private final SecurityContextHelper securityContextHelper;

    public FileMetadataLifecycleService(FileMetadataRepository fileMetadataRepository,
            SecurityContextHelper securityContextHelper) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.securityContextHelper = securityContextHelper;
    }

    /**
     * Creates a queued metadata row for the current tenant and user.
     *
     * @param storedFile stored file descriptor
     * @return persisted metadata
     */
    @Transactional(rollbackFor = Exception.class)
    public FileMetadata createQueued(FileStorageObject storedFile) {
        FileMetadata metadata = new FileMetadata();
        metadata.setFileId(storedFile.fileId());
        metadata.setFilename(storedFile.filename());
        metadata.setContentType(storedFile.contentType());
        metadata.setSize(storedFile.size());
        metadata.setPath(storedFile.path());
        metadata.setContentHash(storedFile.contentHash());
        metadata.setProcessingStatus(FileProcessingStatus.QUEUED);
        metadata.setTenantId(securityContextHelper.getCurrentTenantId());
        metadata.setUploadedBy(securityContextHelper.getCurrentUserId());
        return fileMetadataRepository.saveAndFlush(metadata);
    }

    /**
     * Lists files visible to the current tenant.
     *
     * @return tenant files
     */
    @Transactional(readOnly = true)
    public List<FileMetadata> listCurrentTenant() {
        return fileMetadataRepository.findByTenantId(securityContextHelper.getCurrentTenantId());
    }

    /**
     * Finds one file visible to the current tenant.
     *
     * @param fileId file id
     * @return matched metadata
     */
    @Transactional(readOnly = true)
    public Optional<FileMetadata> findCurrentTenantFile(String fileId) {
        return fileMetadataRepository.findByFileIdAndTenantId(fileId, securityContextHelper.getCurrentTenantId());
    }

    /**
     * 按内容哈希查找当前租户下已处理完成的相同文件（用于上传去重）。
     *
     * @param contentHash SHA-256 hex digest
     * @return 已有的同内容文件
     */
    @Transactional(readOnly = true)
    public Optional<FileMetadata> findCompletedDuplicate(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return Optional.empty();
        }
        return fileMetadataRepository.findFirstByContentHashAndTenantIdAndProcessingStatus(
                contentHash, securityContextHelper.getCurrentTenantId(), FileProcessingStatus.COMPLETED);
    }

    /**
     * Marks a queued file as processing before expensive parsing begins.
     *
     * @param fileId file id
     * @return updated metadata
     */
    @Transactional(rollbackFor = Exception.class)
    public FileMetadata markProcessing(String fileId) {
        FileMetadata metadata = findRequired(fileId);
        metadata.setProcessingStatus(FileProcessingStatus.PROCESSING);
        metadata.setProcessingError(null);
        return fileMetadataRepository.save(metadata);
    }

    /**
     * Persists parsed content and marks a file as completed.
     *
     * @param fileId file id
     * @param content parsed content
     */
    @Transactional(rollbackFor = Exception.class)
    public void markCompleted(String fileId, String content) {
        FileMetadata metadata = findRequired(fileId);
        metadata.setContent(content);
        metadata.setProcessedAt(LocalDateTime.now());
        metadata.setProcessingStatus(FileProcessingStatus.COMPLETED);
        metadata.setProcessingError(null);
        fileMetadataRepository.save(metadata);
    }

    /**
     * Marks a file as failed and keeps the error message bounded for database storage.
     *
     * @param fileId file id
     * @param errorMessage failure reason
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String fileId, String errorMessage) {
        fileMetadataRepository.findById(fileId).ifPresent(metadata -> {
            metadata.setProcessingStatus(FileProcessingStatus.FAILED);
            metadata.setProcessingError(truncate(errorMessage, MAX_ERROR_MESSAGE_LENGTH));
            metadata.setProcessedAt(LocalDateTime.now());
            fileMetadataRepository.save(metadata);
        });
    }

    /**
     * Marks a file as failed because the async processing queue rejected it.
     *
     * @param fileId file id
     * @param errorMessage rejection reason
     */
    @Transactional(rollbackFor = Exception.class)
    public void markRejected(String fileId, String errorMessage) {
        markFailed(fileId, errorMessage);
    }

    /**
     * Deletes metadata after storage cleanup has succeeded.
     *
     * @param metadata metadata to delete
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(FileMetadata metadata) {
        fileMetadataRepository.delete(metadata);
    }

    private FileMetadata findRequired(String fileId) {
        return fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("文件不存在: " + fileId));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
