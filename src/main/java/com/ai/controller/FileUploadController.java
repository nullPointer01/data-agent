package com.ai.controller;

import com.ai.file.dto.FileListResponse;
import com.ai.file.dto.FileMutationResponse;
import com.ai.file.dto.FileResponse;
import com.ai.service.file.FileProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 当前用户上传文件的处理和查询接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileUploadController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadController.class);

    private final FileProcessingService fileProcessingService;

    public FileUploadController(FileProcessingService fileProcessingService) {
        this.fileProcessingService = fileProcessingService;
    }

    /**
     * 上传文件并提交异步解析和索引任务。
     *
     * @param file 待上传文件
     * @return 文件元数据和处理状态
     */
    @PostMapping("/upload")
    public FileResponse uploadFile(@RequestParam("file") MultipartFile file) {
        LOGGER.info("File upload request: {}", file.getOriginalFilename());
        return fileProcessingService.processFile(file);
    }

    /**
     * 查询当前用户上传的文件列表。
     *
     * @return 文件列表
     */
    @GetMapping("/list")
    public FileListResponse listFiles() {
        return fileProcessingService.listFiles();
    }

    /**
     * 查询当前用户拥有的文件信息。
     *
     * @param fileId 文件编号
     * @return 文件元数据和处理状态
     */
    @GetMapping("/{fileId}")
    public FileResponse getFileInfo(@PathVariable String fileId) {
        return fileProcessingService.getFileInfo(fileId);
    }

    /**
     * 删除当前用户拥有的文件及其索引数据。
     *
     * @param fileId 文件编号
     * @return 删除结果
     */
    @DeleteMapping("/delete/{fileId}")
    public FileMutationResponse deleteFile(@PathVariable String fileId) {
        return fileProcessingService.deleteFile(fileId);
    }
}
