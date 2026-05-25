package com.ai.service.file;

/**
 * Immutable description of a file persisted in local upload storage.
 *
 * @param fileId stable file id
 * @param filename normalized display filename
 * @param contentType client-provided content type
 * @param size file size in bytes
 * @param path absolute storage path
 * @author data-agent
 */
public record FileStorageObject(
        String fileId,
        String filename,
        String contentType,
        long size,
        String path) {
}
