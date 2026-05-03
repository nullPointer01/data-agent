package com.ai.controller;

import com.ai.service.FileProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);
    private final FileProcessingService fileProcessingService;

    public FileUploadController(FileProcessingService fileProcessingService) {
        this.fileProcessingService = fileProcessingService;
    }

    @PostMapping("/upload")
    public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file) {
        log.info("File upload request: {}", file.getOriginalFilename());
        return fileProcessingService.processFile(file);
    }

    @GetMapping("/list")
    public Map<String, Object> listFiles() {
        return fileProcessingService.listFiles();
    }

    @DeleteMapping("/delete/{fileId}")
    public Map<String, Object> deleteFile(@PathVariable String fileId) {
        return fileProcessingService.deleteFile(fileId);
    }
}
