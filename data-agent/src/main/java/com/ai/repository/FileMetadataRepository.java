package com.ai.repository;

import com.ai.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, String> {
    List<FileMetadata> findByTenantId(String tenantId);
    List<FileMetadata> findByTenantIdAndUploadedBy(String tenantId, String uploadedBy);
}
