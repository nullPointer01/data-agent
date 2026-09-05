package com.ai.file.dto;

import com.ai.model.FileMetadata;
import com.ai.model.FileProcessingStatus;

import java.time.LocalDateTime;

/**
 * 文件元数据响应。
 *
 * @author data-agent
 */
public record FileResponse(
        boolean success,
        String fileId,
        String filename,
        String contentType,
        long size,
        LocalDateTime uploadedAt,
        String processingStatus,
        String processingError,
        LocalDateTime processedAt,
        String message) {

    private static final String UNKNOWN_CONTENT_TYPE = "unknown";

    public static FileResponse from(FileMetadata metadata) {
        return new FileResponse(
                true,
                metadata.getFileId(),
                metadata.getFilename(),
                defaultContentType(metadata.getContentType()),
                metadata.getSize(),
                metadata.getUploadedAt(),
                statusName(metadata.getProcessingStatus()),
                metadata.getProcessingError(),
                metadata.getProcessedAt(),
                null);
    }

    public static FileResponse accepted(FileMetadata metadata, String message) {
        FileResponse response = from(metadata);
        return new FileResponse(
                true,
                response.fileId(),
                response.filename(),
                response.contentType(),
                response.size(),
                response.uploadedAt(),
                response.processingStatus(),
                response.processingError(),
                response.processedAt(),
                message);
    }

    public static FileResponse failure(String message) {
        return new FileResponse(false, null, null, null, 0L, null, null, null, null, message);
    }

    public static FileResponse failure(String fileId, String message) {
        return new FileResponse(false, fileId, null, null, 0L, null, null, null, null, message);
    }

    private static String defaultContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? UNKNOWN_CONTENT_TYPE : contentType;
    }

    private static String statusName(FileProcessingStatus status) {
        return status == null ? "" : status.name();
    }
}
