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
 * REST API for uploaded files.
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

    @PostMapping("/upload")
    public FileResponse uploadFile(@RequestParam("file") MultipartFile file) {
        LOGGER.info("File upload request: {}", file.getOriginalFilename());
        return fileProcessingService.processFile(file);
    }

    @GetMapping("/list")
    public FileListResponse listFiles() {
        return fileProcessingService.listFiles();
    }

    @GetMapping("/{fileId}")
    public FileResponse getFileInfo(@PathVariable String fileId) {
        return fileProcessingService.getFileInfo(fileId);
    }

    @DeleteMapping("/delete/{fileId}")
    public FileMutationResponse deleteFile(@PathVariable String fileId) {
        return fileProcessingService.deleteFile(fileId);
    }
}
