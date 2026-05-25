package com.ai.service.file;

import com.ai.file.dto.FileListResponse;
import com.ai.file.dto.FileMutationResponse;
import com.ai.file.dto.FileResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Application service for file upload and processing lifecycle.
 *
 * @author data-agent
 */
public interface FileProcessingService {

    /**
     * Accepts an uploaded file and starts asynchronous processing.
     *
     * @param file uploaded multipart file
     * @return upload result
     */
    FileResponse processFile(MultipartFile file);

    /**
     * Lists files visible to the current tenant.
     *
     * @return file list
     */
    FileListResponse listFiles();

    /**
     * Deletes one file visible to the current tenant.
     *
     * @param fileId file id
     * @return deletion result
     */
    FileMutationResponse deleteFile(String fileId);

    /**
     * Returns parsed content for one file visible to the current tenant.
     *
     * @param fileId file id
     * @return parsed file content, or null when absent
     */
    String getFileContent(String fileId);

    /**
     * Returns metadata for one file visible to the current tenant.
     *
     * @param fileId file id
     * @return file metadata result
     */
    FileResponse getFileInfo(String fileId);
}
