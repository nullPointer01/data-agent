package com.ai.service.file;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 由所有文件内容解析器共享的规范化文件解析上下文。
 *
 * @param path 文件已在本地存储时的文件系统路径
 * @param multipartFile 文件仍在内存中时的实时上传输入
 * @param filename 原始文件名
 * @param contentType 原始 MIME 类型
 * @author data-agent
 */
public record FileParsingContext(
        Path path,
        MultipartFile multipartFile,
        String filename,
        String contentType) {

    /**
     * 为存储文件创建解析上下文。
     *
     * @param path 存储文件路径
     * @param filename 原始文件名
     * @param contentType 内容类型
     * @return 解析上下文
     */
    public static FileParsingContext from(Path path, String filename, String contentType) {
        return new FileParsingContext(path, null, filename, contentType);
    }

    /**
     * 为实时多部分上传创建解析上下文。
     *
     * @param file 多部分文件
     * @return 解析上下文
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
