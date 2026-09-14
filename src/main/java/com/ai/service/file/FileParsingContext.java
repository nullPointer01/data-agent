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
     * 返回用于扩展名判断的小写文件名。
     *
     * @return 小写文件名
     */
    public String lowerFilename() {
        return FileTypeSupport.lowerName(filename);
    }

    /**
     * 打开文件输入流供解析器读取。
     *
     * @return 文件输入流
     * @throws IOException 文件无法打开时抛出
     */
    public InputStream openInputStream() throws IOException {
        if (path != null) {
            return Files.newInputStream(path);
        }
        return multipartFile.getInputStream();
    }

    /**
     * 按 UTF-8 读取文本文件。
     *
     * @return UTF-8 文本内容
     * @throws IOException 文件无法读取时抛出
     */
    public String readText() throws IOException {
        if (path != null) {
            return Files.readString(path);
        }
        return new String(multipartFile.getBytes(), StandardCharsets.UTF_8);
    }

}
