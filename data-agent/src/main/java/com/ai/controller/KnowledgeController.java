package com.ai.controller;

import com.ai.knowledge.dto.KnowledgeBatchUploadResponse;
import com.ai.knowledge.dto.KnowledgeDetailEnvelope;
import com.ai.knowledge.dto.KnowledgeListResponse;
import com.ai.knowledge.dto.KnowledgeMutationResponse;
import com.ai.knowledge.dto.KnowledgeSearchRequest;
import com.ai.knowledge.dto.KnowledgeSearchResponse;
import com.ai.knowledge.dto.KnowledgeStatsResponse;
import com.ai.knowledge.dto.KnowledgeSyncConfigEnvelope;
import com.ai.knowledge.dto.KnowledgeSyncConfigRequest;
import com.ai.knowledge.dto.KnowledgeTextRequest;
import com.ai.service.knowledge.KnowledgeService;
import com.ai.service.knowledge.KnowledgeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库管理接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeController.class);
    private static final int DEFAULT_SEARCH_TOP_K = 5;

    private final KnowledgeService knowledgeService;
    private final KnowledgeSyncService knowledgeSyncService;

    public KnowledgeController(KnowledgeService knowledgeService,
            KnowledgeSyncService knowledgeSyncService) {
        this.knowledgeService = knowledgeService;
        this.knowledgeSyncService = knowledgeSyncService;
    }

    @PostMapping("/batch-upload")
    public KnowledgeBatchUploadResponse batchUpload(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return new KnowledgeBatchUploadResponse(false, 0, 0, 0, List.of());
        }
        LOGGER.info("Batch knowledge upload: {} files", files.size());
        return knowledgeService.batchUploadAndIndex(files);
    }

    @PostMapping("/upload")
    public KnowledgeMutationResponse uploadKnowledge(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description) {
        LOGGER.info("Knowledge upload request: {}", file.getOriginalFilename());
        return knowledgeService.uploadAndIndex(file, name, description);
    }

    @PostMapping("/add-text")
    public KnowledgeMutationResponse addTextKnowledge(@RequestBody KnowledgeTextRequest request) {
        String content = request.content();
        if (content == null || content.trim().isEmpty()) {
            return KnowledgeMutationResponse.failure("内容不能为空");
        }
        return knowledgeService.addTextKnowledge(request.name(), content, request.description());
    }

    @GetMapping("/list")
    public KnowledgeListResponse listKnowledge() {
        return knowledgeService.listKnowledge();
    }

    @DeleteMapping("/delete/{knowledgeId}")
    public KnowledgeMutationResponse deleteKnowledge(@PathVariable String knowledgeId) {
        return knowledgeService.deleteKnowledge(knowledgeId);
    }

    @GetMapping("/{knowledgeId}")
    public KnowledgeDetailEnvelope getKnowledgeDetail(@PathVariable String knowledgeId) {
        return knowledgeService.getKnowledgeDetail(knowledgeId);
    }

    @PutMapping("/update/{knowledgeId}")
    public KnowledgeMutationResponse updateKnowledge(@PathVariable String knowledgeId,
            @RequestBody KnowledgeTextRequest request) {
        String content = request.content();
        if (content == null || content.trim().isEmpty()) {
            return KnowledgeMutationResponse.failure("内容不能为空");
        }
        return knowledgeService.updateKnowledge(knowledgeId, request.name(), content, request.description());
    }

    @GetMapping("/stats")
    public KnowledgeStatsResponse getStats() {
        return knowledgeService.getStats();
    }

    @PostMapping("/search")
    public KnowledgeSearchResponse searchKnowledge(@RequestBody KnowledgeSearchRequest request) {
        String query = request.query();
        int topK = request.topK() != null ? request.topK() : DEFAULT_SEARCH_TOP_K;
        if (query == null || query.trim().isEmpty()) {
            return KnowledgeSearchResponse.failure("查询内容不能为空");
        }
        return knowledgeService.searchKnowledge(query, topK);
    }

    /**
     * Get automatic synchronization config.
     *
     * @param knowledgeId knowledge id
     * @return sync config response
     */
    @GetMapping("/{knowledgeId}/sync-config")
    public KnowledgeSyncConfigEnvelope getSyncConfig(@PathVariable String knowledgeId) {
        return knowledgeSyncService.getSyncConfig(knowledgeId);
    }

    @PostMapping("/{knowledgeId}/sync-config")
    public KnowledgeMutationResponse saveSyncConfig(@PathVariable String knowledgeId,
            @RequestBody KnowledgeSyncConfigRequest request) {
        return knowledgeSyncService.saveSyncConfig(knowledgeId, request);
    }

    @DeleteMapping("/{knowledgeId}/sync-config")
    public KnowledgeMutationResponse deleteSyncConfig(@PathVariable String knowledgeId) {
        return knowledgeSyncService.deleteSyncConfig(knowledgeId);
    }

    @PostMapping("/{knowledgeId}/sync-now")
    public KnowledgeMutationResponse syncNow(@PathVariable String knowledgeId) {
        return knowledgeSyncService.triggerSync(knowledgeId);
    }
}
