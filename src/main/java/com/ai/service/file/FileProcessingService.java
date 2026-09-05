package com.ai.service.file;

import com.ai.file.dto.FileListResponse;
import com.ai.file.dto.FileMutationResponse;
import com.ai.file.dto.FileResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传和处理生命周期的应用服务。
 *
 * @author data-agent
 */
public interface FileProcessingService {

    /**
     * 接受上传的文件并启动异步处理。
     *
     * @param file 上传的多部分文件
     * @return 上传结果
     */
    FileResponse processFile(MultipartFile file);

    /**
     * 列出对当前租户可见的文件。
     *
     * @return 文件列表
     */
    FileListResponse listFiles();

    /**
     * 删除对当前租户可见的一个文件。
     *
     * @param fileId 文件 ID
     * @return 删除结果
     */
    FileMutationResponse deleteFile(String fileId);

    /**
     * 返回对当前租户可见的一个文件的解析内容。
     *
     * @param fileId 文件 ID
     * @return 解析的文件内容，缺失时返回 null
     */
    String getFileContent(String fileId);

    /**
     * 返回对当前租户可见的一个文件的元数据。
     *
     * @param fileId 文件 ID
     * @return 文件元数据结果
     */
    FileResponse getFileInfo(String fileId);
}
