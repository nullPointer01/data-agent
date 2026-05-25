package com.ai.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Handles upload filesystem operations and guards all paths inside the managed upload directory.
 *
 * @author data-agent
 */
@Service
public class FileStorageService {

    private static final String DEFAULT_FILENAME = "unknown";
    private static final String CONTROL_CHAR_REGEX = "[\\p{Cntrl}]";
    private static final String CONTROL_CHAR_REPLACEMENT = "_";
    private static final String FILE_SEPARATOR = "/";
    private static final String WINDOWS_FILE_SEPARATOR = "\\";
    private static final int MAX_SAFE_FILENAME_LENGTH = 100;

    private final Path uploadDir;

    public FileStorageService(@Value("${data-agent.file.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        ensureUploadDir();
    }

    /**
     * Stores an uploaded multipart file and returns normalized metadata for persistence.
     *
     * @param fileId generated file id
     * @param file uploaded multipart file
     * @return stored file descriptor
     * @throws IOException when the upload stream cannot be written
     */
    public FileStorageObject store(String fileId, MultipartFile file) throws IOException {
        String filename = normalizeFilename(file.getOriginalFilename());
        String safeFilename = truncateFromLeft(filename, MAX_SAFE_FILENAME_LENGTH);
        Path filePath = uploadDir.resolve(fileId + "_" + safeFilename).normalize();
        validateManagedPath(filePath);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        return new FileStorageObject(fileId, filename, file.getContentType(), file.getSize(), filePath.toString());
    }

    /**
     * Deletes a file from managed upload storage.
     *
     * @param path absolute file path saved in metadata
     * @throws IOException when filesystem deletion fails
     */
    public void delete(String path) throws IOException {
        if (path == null || path.isBlank()) {
            return;
        }
        Path filePath = Paths.get(path).toAbsolutePath().normalize();
        validateManagedPath(filePath);
        Files.deleteIfExists(filePath);
    }

    private void ensureUploadDir() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建文件上传目录: " + uploadDir, e);
        }
    }

    private String normalizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return DEFAULT_FILENAME;
        }

        String unixStyleName = originalFilename.replace(WINDOWS_FILE_SEPARATOR, FILE_SEPARATOR);
        int lastSeparatorIndex = unixStyleName.lastIndexOf(FILE_SEPARATOR);
        String filename = lastSeparatorIndex >= 0 ? unixStyleName.substring(lastSeparatorIndex + 1) : unixStyleName;
        filename = filename.replaceAll(CONTROL_CHAR_REGEX, CONTROL_CHAR_REPLACEMENT).trim();
        return filename.isBlank() ? DEFAULT_FILENAME : filename;
    }

    private String truncateFromLeft(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(value.length() - maxLength);
    }

    private void validateManagedPath(Path filePath) {
        if (!filePath.toAbsolutePath().normalize().startsWith(uploadDir)) {
            throw new SecurityException("非法文件路径: " + filePath);
        }
    }
}
