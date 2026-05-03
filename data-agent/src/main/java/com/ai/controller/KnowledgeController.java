package com.ai.controller;

import com.ai.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);
    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/upload")
    public Map<String, Object> uploadKnowledge(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description) {
        log.info("Knowledge upload request: {}", file.getOriginalFilename());
        return knowledgeService.uploadAndIndex(file, name, description);
    }

    @PostMapping("/add-text")
    public Map<String, Object> addTextKnowledge(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String content = request.get("content");
        String description = request.get("description");
        if (content == null || content.trim().isEmpty()) {
            return Map.of("success", false, "message", "内容不能为空");
        }
        return knowledgeService.addTextKnowledge(name, content, description);
    }

    @GetMapping("/list")
    public Map<String, Object> listKnowledge() {
        return knowledgeService.listKnowledge();
    }

    @DeleteMapping("/delete/{knowledgeId}")
    public Map<String, Object> deleteKnowledge(@PathVariable String knowledgeId) {
        return knowledgeService.deleteKnowledge(knowledgeId);
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return knowledgeService.getStats();
    }

    @PostMapping("/search")
    public Map<String, Object> searchKnowledge(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        int topK = request.containsKey("topK") ? ((Number) request.get("topK")).intValue() : 5;
        if (query == null || query.trim().isEmpty()) {
            return Map.of("success", false, "message", "查询内容不能为空");
        }
        return knowledgeService.searchKnowledge(query, topK);
    }
}
