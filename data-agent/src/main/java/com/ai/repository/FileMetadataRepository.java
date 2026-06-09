package com.ai.repository;

import com.ai.model.FileMetadata;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ai.model.FileProcessingStatus;

import java.util.List;
import java.util.Optional;

/**
 * 上传文件元数据仓储。
 *
 * @author data-agent
 */
public interface FileMetadataRepository extends JpaRepository<FileMetadata, String> {

    /**
     * 查询租户文件。
     *
     * @param tenantId 租户编号
     * @return 租户文件
     */
    List<FileMetadata> findByTenantId(String tenantId);

    /**
     * 查询租户用户上传的文件。
     *
     * @param tenantId 租户编号
     * @param uploadedBy 用户编号
     * @return 用户文件
     */
    List<FileMetadata> findByTenantIdAndUploadedBy(String tenantId, String uploadedBy);

    /**
     * 按租户查询单个文件。
     *
     * @param fileId 文件编号
     * @param tenantId 租户编号
     * @return 文件元数据
     */
    Optional<FileMetadata> findByFileIdAndTenantId(String fileId, String tenantId);

    /**
     * 按内容哈希和租户查询已完成处理的文件（用于上传去重）。
     *
     * @param contentHash SHA-256 hex digest
     * @param tenantId 租户编号
     * @param status 期望的处理状态（通常为 COMPLETED）
     * @return 匹配的已有文件
     */
    Optional<FileMetadata> findFirstByContentHashAndTenantIdAndProcessingStatus(
            String contentHash, String tenantId, FileProcessingStatus status);

    /**
     * JPA 全文检索兜底查询。
     *
     * @param tenantId 租户编号
     * @param status 文件处理状态名称
     * @param keyword 已转义的检索词
     * @param pageable 分页参数
     * @return 命中的文件
     */
    @Query(value = """
            SELECT * FROM file_metadata f
            WHERE f.tenant_id = :tenantId
              AND f.processing_status = :status
              AND (
                LOWER(f.filename) LIKE :pattern
                OR (f.content IS NOT NULL AND LOWER(f.content) LIKE :pattern)
              )
            ORDER BY f.uploaded_at DESC
            """, nativeQuery = true)
    List<FileMetadata> searchByTenantAndKeyword(@Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("pattern") String pattern,
            Pageable pageable);
}
