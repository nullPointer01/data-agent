package com.ai.service.file;

import com.ai.model.FileMetadata;
import com.ai.model.FileProcessingStatus;
import com.ai.indexing.IndexTask;
import com.ai.indexing.IndexTaskStore;
import com.ai.repository.FileMetadataRepository;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 管理租户范围内的文件元数据状态转换。
 *
 * @author data-agent
 */
@Service
public class FileMetadataLifecycleService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final FileMetadataRepository fileMetadataRepository;
    private final SecurityContextHelper securityContextHelper;
    private final IndexTaskStore tasks;

    public FileMetadataLifecycleService(FileMetadataRepository fileMetadataRepository,
            SecurityContextHelper securityContextHelper, IndexTaskStore tasks) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.securityContextHelper = securityContextHelper;
        this.tasks = tasks;
    }

    /**
     * 为当前租户和用户创建排队中的元数据行。
     *
     * @param storedFile 存储文件描述符
     * @return 持久化的元数据
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
        FileMetadata saved = fileMetadataRepository.saveAndFlush(metadata);
        tasks.enqueue(IndexTask.Kind.FILE, saved.getTenantId(), saved.getUploadedBy(), saved.getFileId(), null);
        return saved;
    }

    /**
     * 列出当前用户拥有的文件。
     *
     * @return tenant files
     */
    @Transactional(readOnly = true)
    public List<FileMetadata> listCurrentTenant() {
        return fileMetadataRepository.findByTenantIdAndUploadedBy(
                securityContextHelper.getCurrentTenantId(), securityContextHelper.getCurrentUserId());
    }

    /**
     * 按文件、用户和租户共同查找文件。
     *
     * @param fileId file id
     * @return matched metadata
     */
    @Transactional(readOnly = true)
    public Optional<FileMetadata> findCurrentTenantFile(String fileId) {
        return fileMetadataRepository.findByFileIdAndTenantIdAndUploadedBy(
                fileId, securityContextHelper.getCurrentTenantId(), securityContextHelper.getCurrentUserId());
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
        return fileMetadataRepository.findFirstByContentHashAndTenantIdAndUploadedByAndProcessingStatus(
                contentHash, securityContextHelper.getCurrentTenantId(), securityContextHelper.getCurrentUserId(),
                FileProcessingStatus.COMPLETED);
    }

    /**
     * 使用持久任务身份把文件标记为处理中，不读取调度线程登录上下文。
     *
     * @param fileId file id
     * @return updated metadata
     */
    @Transactional(rollbackFor = Exception.class)
    public FileMetadata markProcessing(String fileId, String tenant, String user) {
        FileMetadata metadata = findRequired(fileId, tenant, user);
        metadata.setProcessingStatus(FileProcessingStatus.PROCESSING);
        metadata.setProcessingError(null);
        return fileMetadataRepository.save(metadata);
    }

    /**
     * 双索引写入完成后保存解析正文，文件状态才可变为完成。
     *
     * @param fileId file id
     * @param content parsed content
     */
    @Transactional(rollbackFor = Exception.class)
    public void markCompleted(String fileId, String tenant, String user, String content) {
        FileMetadata metadata = findRequired(fileId, tenant, user);
        metadata.setContent(content);
        metadata.setProcessedAt(LocalDateTime.now());
        metadata.setProcessingStatus(FileProcessingStatus.COMPLETED);
        metadata.setProcessingError(null);
        fileMetadataRepository.save(metadata);
    }

    /**
     * 标记处理失败并限制错误长度；已删除文件不重新创建元数据。
     *
     * @param fileId file id
     * @param errorMessage failure reason
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String fileId, String tenant, String user, String errorMessage) {
        fileMetadataRepository.findByFileIdAndTenantIdAndUploadedBy(fileId, tenant, user).ifPresent(metadata -> {
            metadata.setProcessingStatus(FileProcessingStatus.FAILED);
            metadata.setProcessingError(truncate(errorMessage, MAX_ERROR_MESSAGE_LENGTH));
            metadata.setProcessedAt(LocalDateTime.now());
            fileMetadataRepository.save(metadata);
        });
    }

    /**
     * 删除元数据并在同一事务中提交索引清理任务，外部清理可持久重试。
     *
     * @param metadata metadata to delete
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(FileMetadata metadata) {
        fileMetadataRepository.delete(metadata);
        // 失效的旧执行可能已写外部索引，推进版本让后台再做一次来源清理。
        tasks.enqueue(IndexTask.Kind.FILE, metadata.getTenantId(), metadata.getUploadedBy(), metadata.getFileId(), null);
    }

    private FileMetadata findRequired(String fileId, String tenant, String user) {
        return fileMetadataRepository.findByFileIdAndTenantIdAndUploadedBy(fileId, tenant, user)
                .orElseThrow(() -> new IllegalArgumentException("文件不存在: " + fileId));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
