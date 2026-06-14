package com.ai.service.file;

/**
 * 本地上传存储中持久化文件的不可变描述。
 *
 * @param fileId 稳定的文件编号
 * @param filename 规范化的显示文件名
 * @param contentType 客户端提供的内容类型
 * @param size 文件大小（字节）
 * @param path 绝对存储路径
 * @param contentHash 文件内容的 SHA-256 十六进制摘要，用于去重
 * @author data-agent
 */
public record FileStorageObject(
        String fileId,
        String filename,
        String contentType,
        long size,
        String path,
        String contentHash) {
}
