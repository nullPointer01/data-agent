package com.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "file_metadata")
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String fileId;

    @Column(nullable = false, length = 256)
    private String filename;

    @Column(name = "content_type", length = 128)
    private String contentType;

    private long size;

    @Column(length = 512)
    private String path;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "uploaded_by", length = 64)
    private String uploadedBy;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }

    public FileMetadata() {}

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
