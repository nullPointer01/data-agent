package com.ai.service;

import com.ai.model.FileMetadata;
import com.ai.repository.FileMetadataRepository;
import com.ai.security.SecurityContextHelper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class FileProcessingServiceImpl implements FileProcessingService {

    private static final Logger log = LoggerFactory.getLogger(FileProcessingServiceImpl.class);
    private static final String UPLOAD_DIR = "uploads";

    private final FileMetadataRepository fileMetadataRepository;
    private final SecurityContextHelper securityContextHelper;
    private final ApplicationEventPublisher eventPublisher;
    private final VectorMemoryService vectorMemoryService;

    public FileProcessingServiceImpl(FileMetadataRepository fileMetadataRepository,
            SecurityContextHelper securityContextHelper,
            ApplicationEventPublisher eventPublisher,
            VectorMemoryService vectorMemoryService) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.securityContextHelper = securityContextHelper;
        this.eventPublisher = eventPublisher;
        this.vectorMemoryService = vectorMemoryService;
        ensureUploadDir();
    }

    private void ensureUploadDir() {
        Path dir = Paths.get(UPLOAD_DIR);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                log.error("Failed to create upload directory", e);
            }
        }
    }

    @Override
    @Transactional
    public Map<String, Object> processFile(MultipartFile file) {
        try {
            String fileId = UUID.randomUUID().toString();
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();
            long size = file.getSize();

            Path filePath = Paths.get(UPLOAD_DIR, fileId + "_" + originalFilename);
            Files.write(filePath, file.getBytes());

            String content = parseFile(file, contentType);

            FileMetadata metadata = new FileMetadata();
            metadata.setFileId(fileId);
            metadata.setFilename(originalFilename);
            metadata.setContentType(contentType);
            metadata.setSize(size);
            metadata.setPath(filePath.toString());
            metadata.setContent(content);
            metadata.setTenantId(securityContextHelper.getCurrentTenantId());
            metadata.setUploadedBy(securityContextHelper.getCurrentUserId());

            fileMetadataRepository.save(metadata);
            log.info("File uploaded: {}, tenant: {}", originalFilename, metadata.getTenantId());

            if (content != null && !content.isEmpty() && content.length() > 50 && !content.startsWith("Image file")
                    && !content.startsWith("Unsupported")) {
                eventPublisher.publishEvent(new FileUploadedEvent(this, fileId, originalFilename, content));

                try {
                    vectorMemoryService.indexFile(fileId, originalFilename, content);
                } catch (Exception e) {
                    log.warn("Failed to index file to vector memory: {}", originalFilename, e);
                }
            }

            return Map.of("success", true, "fileId", fileId,
                    "filename", originalFilename != null ? originalFilename : "unknown",
                    "contentType", contentType != null ? contentType : "unknown",
                    "size", size, "message", "文件上传成功");
        } catch (Exception e) {
            log.error("File upload failed", e);
            return Map.of("success", false, "message", "文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> listFiles() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<FileMetadata> files = fileMetadataRepository.findByTenantId(tenantId);
        return Map.of("success", true, "files", files);
    }

    @Override
    @Transactional
    public Map<String, Object> deleteFile(String fileId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        var files = fileMetadataRepository.findByTenantId(tenantId);
        var target = files.stream().filter(f -> f.getFileId().equals(fileId)).findFirst();

        if (target.isEmpty()) {
            return Map.of("success", false, "message", "文件不存在或无权限");
        }

        try {
            Files.deleteIfExists(Paths.get(target.get().getPath()));
            fileMetadataRepository.delete(target.get());
            try {
                vectorMemoryService.removeFromStore("file", fileId);
            } catch (Exception ve) {
                log.warn("Failed to remove file from vector store: {}", fileId, ve);
            }
            return Map.of("success", true, "message", "文件删除成功");
        } catch (IOException e) {
            log.error("File deletion failed", e);
            return Map.of("success", false, "message", "文件删除失败: " + e.getMessage());
        }
    }

    @Override
    public String getFileContent(String fileId) {
        return fileMetadataRepository.findById(fileId)
                .map(FileMetadata::getContent)
                .orElse(null);
    }

    @Override
    public Map<String, Object> getFileInfo(String fileId) {
        return fileMetadataRepository.findById(fileId)
                .map(f -> Map.<String, Object>of("success", true, "fileId", f.getFileId(),
                        "filename", f.getFilename(), "contentType", f.getContentType(),
                        "size", f.getSize(), "uploadedAt",
                        f.getUploadedAt() != null ? f.getUploadedAt().toString() : ""))
                .orElse(Map.of("success", false, "message", "文件不存在"));
    }

    private String parseFile(MultipartFile file, String contentType) throws Exception {
        String filename = file.getOriginalFilename();
        if (contentType != null) {
            if (contentType.contains("excel") || contentType.contains("spreadsheet") ||
                    (filename != null && (filename.endsWith(".xlsx") || filename.endsWith(".xls")))) {
                return parseExcel(file.getInputStream());
            } else if (contentType.contains("pdf") || (filename != null && filename.endsWith(".pdf"))) {
                return parsePdf(file.getInputStream());
            } else if (contentType.contains("image")) {
                return "Image file: " + filename + " (" + file.getSize() + " bytes)";
            } else if (contentType.contains("text") || contentType.contains("csv")) {
                return new String(file.getBytes());
            }
        }
        return "Unsupported file type";
    }

    private String parseExcel(InputStream inputStream) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                content.append("Sheet: ").append(sheet.getSheetName()).append("\n");
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row != null) {
                        for (int c = 0; c < row.getLastCellNum(); c++) {
                            Cell cell = row.getCell(c);
                            content.append(cell != null ? getCellValue(cell) : "").append("\t");
                        }
                        content.append("\n");
                    }
                }
                content.append("\n");
            }
            return content.toString();
        }
    }

    private String parsePdf(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? cell.getDateCellValue().toString()
                    : String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
