package com.ai.model;

import java.util.UUID;

import java.time.LocalDateTime;

/**
 * 上传文件的元数据和处理状态。
 *
 * @author data-agent
 */
public class FileMetadata {

    private String fileId;

    private String filename;

    private String contentType;

    private long size;

    private String path;

    private String content;

    private FileProcessingStatus processingStatus = FileProcessingStatus.QUEUED;

    private String processingError;

    private LocalDateTime processedAt;

    private String tenantId;

    private String uploadedBy;

    private LocalDateTime uploadedAt;

    private String contentHash;

    public void onCreate() {
        if (fileId == null) {
            fileId = UUID.randomUUID().toString();
        }
        uploadedAt = LocalDateTime.now();
    }

    public FileMetadata() {
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public FileProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(FileProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getProcessingError() {
        return processingError;
    }

    public void setProcessingError(String processingError) {
        this.processingError = processingError;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
}
