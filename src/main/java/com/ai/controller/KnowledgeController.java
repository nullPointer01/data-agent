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

    /**
     * 批量上传知识文件并分别执行解析和索引。
     *
     * @param files 待上传文件列表
     * @return 批量处理汇总和每个文件的结果
     */
    @PostMapping("/batch-upload")
    public KnowledgeBatchUploadResponse batchUpload(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return new KnowledgeBatchUploadResponse(false, 0, 0, 0, List.of());
        }
        LOGGER.info("Batch knowledge upload: {} files", files.size());
        return knowledgeService.batchUploadAndIndex(files);
    }

    /**
     * 上传单个知识文件并建立检索索引。
     *
     * @param file 待上传文件
     * @param name 可选的知识名称
     * @param description 可选的知识描述
     * @return 创建和索引结果
     */
    @PostMapping("/upload")
    public KnowledgeMutationResponse uploadKnowledge(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description) {
        LOGGER.info("Knowledge upload request: {}", file.getOriginalFilename());
        return knowledgeService.uploadAndIndex(file, name, description);
    }

    /**
     * 使用文本内容创建知识条目并建立索引。
     *
     * @param request 知识名称、正文和描述
     * @return 创建和索引结果
     */
    @PostMapping("/add-text")
    public KnowledgeMutationResponse addTextKnowledge(@RequestBody KnowledgeTextRequest request) {
        String content = request.content();
        if (content == null || content.trim().isEmpty()) {
            return KnowledgeMutationResponse.failure("内容不能为空");
        }
        return knowledgeService.addTextKnowledge(request.name(), content, request.description());
    }

    /**
     * 查询当前租户的知识条目列表。
     *
     * @return 知识列表
     */
    @GetMapping("/list")
    public KnowledgeListResponse listKnowledge() {
        return knowledgeService.listKnowledge();
    }

    /**
     * 删除当前租户拥有的知识条目及其索引。
     *
     * @param knowledgeId 知识编号
     * @return 删除结果
     */
    @DeleteMapping("/delete/{knowledgeId}")
    public KnowledgeMutationResponse deleteKnowledge(@PathVariable String knowledgeId) {
        return knowledgeService.deleteKnowledge(knowledgeId);
    }

    /**
     * 查询当前租户拥有的知识条目详情。
     *
     * @param knowledgeId 知识编号
     * @return 知识详情
     */
    @GetMapping("/{knowledgeId}")
    public KnowledgeDetailEnvelope getKnowledgeDetail(@PathVariable String knowledgeId) {
        return knowledgeService.getKnowledgeDetail(knowledgeId);
    }

    /**
     * 更新当前租户拥有的文本知识并重建索引。
     *
     * @param knowledgeId 知识编号
     * @param request 新的知识名称、正文和描述
     * @return 更新和重建索引结果
     */
    @PutMapping("/update/{knowledgeId}")
    public KnowledgeMutationResponse updateKnowledge(@PathVariable String knowledgeId,
            @RequestBody KnowledgeTextRequest request) {
        String content = request.content();
        if (content == null || content.trim().isEmpty()) {
            return KnowledgeMutationResponse.failure("内容不能为空");
        }
        return knowledgeService.updateKnowledge(knowledgeId, request.name(), content, request.description());
    }

    /**
     * 汇总当前租户的知识条目和索引统计。
     *
     * @return 知识统计
     */
    @GetMapping("/stats")
    public KnowledgeStatsResponse getStats() {
        return knowledgeService.getStats();
    }

    /**
     * 在当前租户知识库中检索相关内容。
     *
     * @param request 查询文本和可选返回数量
     * @return 知识检索结果
     */
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
     * 查询当前用户拥有的知识同步配置。
     *
     * @param knowledgeId 知识编号
     * @return 同步配置
     */
    @GetMapping("/{knowledgeId}/sync-config")
    public KnowledgeSyncConfigEnvelope getSyncConfig(@PathVariable String knowledgeId) {
        return knowledgeSyncService.getSyncConfig(knowledgeId);
    }

    /**
     * 保存当前用户拥有的知识同步配置。
     *
     * @param knowledgeId 知识编号
     * @param request 同步类型、来源和调度配置
     * @return 保存结果
     */
    @PostMapping("/{knowledgeId}/sync-config")
    public KnowledgeMutationResponse saveSyncConfig(@PathVariable String knowledgeId,
            @RequestBody KnowledgeSyncConfigRequest request) {
        return knowledgeSyncService.saveSyncConfig(knowledgeId, request);
    }

    /**
     * 删除当前用户拥有的知识同步配置。
     *
     * @param knowledgeId 知识编号
     * @return 删除结果
     */
    @DeleteMapping("/{knowledgeId}/sync-config")
    public KnowledgeMutationResponse deleteSyncConfig(@PathVariable String knowledgeId) {
        return knowledgeSyncService.deleteSyncConfig(knowledgeId);
    }

    /**
     * 立即执行一次当前用户拥有的知识同步任务。
     *
     * @param knowledgeId 知识编号
     * @return 同步触发结果
     */
    @PostMapping("/{knowledgeId}/sync-now")
    public KnowledgeMutationResponse syncNow(@PathVariable String knowledgeId) {
        return knowledgeSyncService.triggerSync(knowledgeId);
    }
}
