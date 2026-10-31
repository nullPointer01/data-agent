package com.ai.repository;

import com.ai.model.FileMetadata;
import com.ai.model.FileProcessingStatus;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** FileMetadata 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface FileMetadataRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default FileMetadata save(FileMetadata entity) {
        boolean creating = entity.getFileId() == null || !existsById(entity.getFileId());
        if (creating) {
            entity.onCreate();
            insert(entity);
        } else {
            if (update(entity) != 1) {
                throw new OptimisticLockingFailureException("记录已变化，请重新读取后保存");
            }
        }
        return entity;
    }

    int insert(FileMetadata entity);
    int update(FileMetadata entity);
    Optional<FileMetadata> findById(@Param("id") String id);
    boolean existsById(@Param("id") String id);
    List<FileMetadata> findAll();
    List<FileMetadata> findAllById(@Param("ids") Collection<String> ids);
    void deleteById(@Param("id") String id);

    default void delete(FileMetadata entity) {
        deleteById(entity.getFileId());
    }

    @Transactional
    default List<FileMetadata> saveAll(Iterable<FileMetadata> entities) {
        List<FileMetadata> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    List<FileMetadata> findByTenantIdAndUploadedBy(@Param("tenantId") String tenantId, @Param("uploadedBy") String uploadedBy);

    Optional<FileMetadata> findByFileIdAndTenantId(@Param("fileId") String fileId, @Param("tenantId") String tenantId);

    Optional<FileMetadata> findByFileIdAndTenantIdAndUploadedBy(@Param("fileId") String fileId,
            @Param("tenantId") String tenantId,
            @Param("uploadedBy") String uploadedBy);

    Optional<FileMetadata> findFirstByContentHashAndTenantIdAndProcessingStatus(@Param("contentHash") String contentHash,
            @Param("tenantId") String tenantId,
            @Param("status") FileProcessingStatus status);

    Optional<FileMetadata> findFirstByContentHashAndTenantIdAndUploadedByAndProcessingStatus(@Param("contentHash") String contentHash,
            @Param("tenantId") String tenantId,
            @Param("uploadedBy") String uploadedBy,
            @Param("status") FileProcessingStatus status);
}
