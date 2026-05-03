package com.ai.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface FileProcessingService {
    Map<String, Object> processFile(MultipartFile file);

    Map<String, Object> listFiles();

    Map<String, Object> deleteFile(String fileId);

    String getFileContent(String fileId);

    Map<String, Object> getFileInfo(String fileId);
}