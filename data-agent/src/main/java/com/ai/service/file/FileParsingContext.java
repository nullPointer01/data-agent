package com.ai.service.file;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Normalized file parsing context shared by all file content parsers.
 *
 * @param path filesystem path when the file has already been stored locally
 * @param multipartFile live upload input when the file is still in memory
 * @param filename original filename
 * @param contentType original mime type
 * @author data-agent
 */
public record FileParsingContext(
        Path path,
        MultipartFile multipartFile,
        String filename,
        String contentType) {

    /**
     * Creates a parsing context for a stored file.
     *
     * @param path stored file path
     * @param filename original filename
     * @param contentType content type
     * @return parsing context
     */
    public static FileParsingContext from(Path path, String filename, String contentType) {
        return new FileParsingContext(path, null, filename, contentType);
    }

    /**
     * Creates a parsing context for a live multipart upload.
     *
     * @param file multipart file
     * @return parsing context
     */
    public static FileParsingContext from(MultipartFile file) {
        return new FileParsingContext(null, file, file.getOriginalFilename(), file.getContentType());
    }

    /**
     * Returns a lower-case filename for extension checks.
     *
     * @return lower-case filename
     */
    public String lowerFilename() {
        return FileTypeSupport.lowerName(filename);
    }

    /**
     * Checks whether the content type is an image mime type.
     *
     * @return true when the file is an image
     */
    public boolean isImage() {
        return FileTypeSupport.isImage(contentType);
    }

    /**
     * Returns the file size in bytes.
     *
     * @return file size
     * @throws IOException when the underlying source cannot be measured
     */
    public long size() throws IOException {
        if (path != null) {
            return Files.size(path);
        }
        return multipartFile.getSize();
    }

    /**
     * Opens the file as an input stream.
     *
     * @return input stream for parsing
     * @throws IOException when the source cannot be opened
     */
    public InputStream openInputStream() throws IOException {
        if (path != null) {
            return Files.newInputStream(path);
        }
        return multipartFile.getInputStream();
    }

    /**
     * Reads the file as UTF-8 text.
     *
     * @return UTF-8 text content
     * @throws IOException when the source cannot be read
     */
    public String readText() throws IOException {
        if (path != null) {
            return Files.readString(path);
        }
        return new String(multipartFile.getBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Builds the standard image placeholder string.
     *
     * @return image placeholder text
     * @throws IOException when file size cannot be resolved
     */
    public String imagePlaceholder() throws IOException {
        return FileTypeSupport.imagePlaceholder(filename, size());
    }
}
