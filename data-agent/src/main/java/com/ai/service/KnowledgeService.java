package com.ai.service;

import com.ai.model.KnowledgeEntry;
import com.ai.repository.KnowledgeEntryRepository;
import com.ai.security.SecurityContextHelper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final KnowledgeEntryRepository knowledgeRepository;
    private final VectorMemoryService vectorMemoryService;
    private final SecurityContextHelper securityContextHelper;

    public KnowledgeService(KnowledgeEntryRepository knowledgeRepository,
                            VectorMemoryService vectorMemoryService,
                            SecurityContextHelper securityContextHelper) {
        this.knowledgeRepository = knowledgeRepository;
        this.vectorMemoryService = vectorMemoryService;
        this.securityContextHelper = securityContextHelper;
    }

    @Transactional
    public Map<String, Object> uploadAndIndex(MultipartFile file, String name, String description) {
        try {
            String filename = file.getOriginalFilename();
            String contentType = file.getContentType();
            String content = parseFile(file, contentType);

            if (content == null || content.trim().isEmpty()) {
                return Map.of("success", false, "message", "无法解析文件内容，请检查文件格式");
            }

            String knowledgeId = UUID.randomUUID().toString();
            String displayName = (name != null && !name.isEmpty()) ? name : filename;

            KnowledgeEntry entry = new KnowledgeEntry();
            entry.setKnowledgeId(knowledgeId);
            entry.setName(displayName);
            entry.setDescription(description != null ? description : "来自文件: " + filename);
            entry.setSourceType("file");
            entry.setSourceFilename(filename);
            entry.setContent(content);
            entry.setContentLength(content.length());
            entry.setTenantId(securityContextHelper.getCurrentTenantId());
            entry.setCreatedBy(securityContextHelper.getCurrentUserId());

            vectorMemoryService.indexKnowledge(content, knowledgeId);
            int chunkCount = estimateChunkCount(content);
            entry.setChunkCount(chunkCount);

            knowledgeRepository.save(entry);

            log.info("Knowledge indexed: {} ({} chunks, {} chars)", displayName, chunkCount, content.length());

            return Map.of(
                    "success", true,
                    "knowledgeId", knowledgeId,
                    "name", displayName,
                    "chunkCount", chunkCount,
                    "contentLength", content.length(),
                    "message", "知识文档已上传并索引成功");
        } catch (Exception e) {
            log.error("Knowledge upload failed", e);
            return Map.of("success", false, "message", "知识上传失败: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> addTextKnowledge(String name, String content, String description) {
        try {
            String knowledgeId = UUID.randomUUID().toString();
            String displayName = (name != null && !name.isEmpty()) ? name : "文本知识-" + knowledgeId.substring(0, 8);

            KnowledgeEntry entry = new KnowledgeEntry();
            entry.setKnowledgeId(knowledgeId);
            entry.setName(displayName);
            entry.setDescription(description != null ? description : "手动添加的文本知识");
            entry.setSourceType("text");
            entry.setContent(content);
            entry.setContentLength(content.length());
            entry.setTenantId(securityContextHelper.getCurrentTenantId());
            entry.setCreatedBy(securityContextHelper.getCurrentUserId());

            vectorMemoryService.indexKnowledge(content, knowledgeId);
            int chunkCount = estimateChunkCount(content);
            entry.setChunkCount(chunkCount);

            knowledgeRepository.save(entry);

            log.info("Text knowledge indexed: {} ({} chunks)", displayName, chunkCount);

            return Map.of(
                    "success", true,
                    "knowledgeId", knowledgeId,
                    "name", displayName,
                    "chunkCount", chunkCount,
                    "message", "文本知识已添加并索引成功");
        } catch (Exception e) {
            log.error("Text knowledge add failed", e);
            return Map.of("success", false, "message", "添加失败: " + e.getMessage());
        }
    }

    public Map<String, Object> listKnowledge() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<KnowledgeEntry> entries = knowledgeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<Map<String, Object>> list = entries.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("knowledgeId", e.getKnowledgeId());
            m.put("name", e.getName());
            m.put("description", e.getDescription());
            m.put("sourceType", e.getSourceType());
            m.put("sourceFilename", e.getSourceFilename());
            m.put("chunkCount", e.getChunkCount());
            m.put("contentLength", e.getContentLength());
            m.put("createdAt", e.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        return Map.of("success", true, "knowledge", list, "total", list.size());
    }

    @Transactional
    public Map<String, Object> deleteKnowledge(String knowledgeId) {
        var opt = knowledgeRepository.findById(knowledgeId);
        if (opt.isEmpty()) {
            return Map.of("success", false, "message", "知识条目不存在");
        }

        try {
            vectorMemoryService.removeFromStore("knowledge", knowledgeId);
            knowledgeRepository.deleteById(knowledgeId);
            log.info("Knowledge deleted: {}", knowledgeId);
            return Map.of("success", true, "message", "知识条目已删除");
        } catch (Exception e) {
            log.error("Knowledge deletion failed", e);
            return Map.of("success", false, "message", "删除失败: " + e.getMessage());
        }
    }

    public Map<String, Object> getStats() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<KnowledgeEntry> entries = knowledgeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        long totalChunks = entries.stream().mapToInt(KnowledgeEntry::getChunkCount).sum();
        long totalChars = entries.stream().mapToLong(KnowledgeEntry::getContentLength).sum();
        int vectorCount = vectorMemoryService.getIndexedCount();
        Map<String, Long> byType = vectorMemoryService.getIndexedCountByType();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("success", true);
        stats.put("knowledgeCount", entries.size());
        stats.put("totalChunks", totalChunks);
        stats.put("totalCharacters", totalChars);
        stats.put("vectorStoreCount", vectorCount);
        stats.put("vectorStoreByType", byType);
        stats.put("usingMilvus", vectorMemoryService.isUsingMilvus());
        return stats;
    }

    public Map<String, Object> searchKnowledge(String query, int topK) {
        String result = vectorMemoryService.searchRelevant(query, topK);
        if (result == null || result.isEmpty()) {
            return Map.of("success", true, "results", List.of(), "message", "未找到相关知识");
        }
        return Map.of("success", true, "results", result);
    }

    private int estimateChunkCount(String content) {
        int chunkSize = 500;
        int overlap = 100;
        int effectiveStep = chunkSize - overlap;
        return (int) Math.ceil((double) content.length() / effectiveStep);
    }

    private String parseFile(MultipartFile file, String contentType) throws Exception {
        String filename = file.getOriginalFilename();
        if (contentType != null) {
            if (contentType.contains("pdf") || (filename != null && filename.endsWith(".pdf"))) {
                return parsePdf(file.getInputStream());
            } else if (contentType.contains("excel") || contentType.contains("spreadsheet")
                    || (filename != null && (filename.endsWith(".xlsx") || filename.endsWith(".xls")))) {
                return parseExcel(file.getInputStream());
            } else if (contentType.contains("text") || contentType.contains("csv")
                    || contentType.contains("json") || contentType.contains("xml")
                    || contentType.contains("markdown")) {
                return new String(file.getBytes());
            }
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".txt") || lower.endsWith(".csv") || lower.endsWith(".md")
                    || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".log")) {
                return new String(file.getBytes());
            }
        }
        return new String(file.getBytes());
    }

    private String parsePdf(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
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
            }
            return content.toString();
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
