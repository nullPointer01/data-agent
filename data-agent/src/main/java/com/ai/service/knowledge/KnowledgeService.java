package com.ai.service.knowledge;

import com.ai.service.file.FileParserService;

import com.ai.service.VectorMemoryService;

import com.ai.knowledge.dto.KnowledgeBatchItemResponse;
import com.ai.knowledge.dto.KnowledgeBatchUploadResponse;
import com.ai.knowledge.dto.KnowledgeDetailEnvelope;
import com.ai.knowledge.dto.KnowledgeDetailResponse;
import com.ai.knowledge.dto.KnowledgeItemResponse;
import com.ai.knowledge.dto.KnowledgeListResponse;
import com.ai.knowledge.dto.KnowledgeMutationResponse;
import com.ai.knowledge.dto.KnowledgeSearchResponse;
import com.ai.knowledge.dto.KnowledgeSearchResult;
import com.ai.knowledge.dto.KnowledgeStatsResponse;
import com.ai.model.KnowledgeEntry;
import com.ai.rag.retrieval.HybridRetriever;
import com.ai.rag.RagQueryAnalysis;
import com.ai.rag.RagQueryRewriter;
import com.ai.rag.RagReranker;
import com.ai.rag.retrieval.RetrievalResult;
import com.ai.repository.KnowledgeEntryRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.vector.VectorDocumentTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Knowledge document management and vector indexing service.
 *
 * @author data-agent
 */
@Service
public class KnowledgeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeService.class);
    private static final int KNOWLEDGE_ID_DISPLAY_LENGTH = 8;
    private static final double DEFAULT_SEARCH_MIN_SCORE = 0.45D;
    private static final String SOURCE_TYPE_FILE = "file";
    private static final String SOURCE_TYPE_TEXT = "text";
    private static final String UNKNOWN_FILENAME = "unknown";
    private static final String DEFAULT_FILE_DESCRIPTION_PREFIX = "来自文件: ";
    private static final String DEFAULT_TEXT_DESCRIPTION = "手动添加的文本知识";
    private static final String TEXT_KNOWLEDGE_NAME_PREFIX = "文本知识-";
    private static final String REFERENCE_PREFIX = "R";

    private final KnowledgeEntryRepository knowledgeRepository;
    private final SecurityContextHelper securityContextHelper;
    private final FileParserService fileParserService;
    private final KnowledgeVectorIndexService knowledgeVectorIndexService;
    private final KnowledgeVectorEventPublisher knowledgeVectorEventPublisher;
    private final HybridRetriever hybridRetriever;
    private final RagQueryRewriter ragQueryRewriter;
    private final RagReranker ragReranker;

    public KnowledgeService(KnowledgeEntryRepository knowledgeRepository,
            SecurityContextHelper securityContextHelper,
            FileParserService fileParserService,
            KnowledgeVectorIndexService knowledgeVectorIndexService,
            KnowledgeVectorEventPublisher knowledgeVectorEventPublisher,
            HybridRetriever hybridRetriever,
            RagQueryRewriter ragQueryRewriter,
            RagReranker ragReranker) {
        this.knowledgeRepository = knowledgeRepository;
        this.securityContextHelper = securityContextHelper;
        this.fileParserService = fileParserService;
        this.knowledgeVectorIndexService = knowledgeVectorIndexService;
        this.knowledgeVectorEventPublisher = knowledgeVectorEventPublisher;
        this.hybridRetriever = hybridRetriever;
        this.ragQueryRewriter = ragQueryRewriter;
        this.ragReranker = ragReranker;
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBatchUploadResponse batchUploadAndIndex(List<MultipartFile> files) {
        int success = 0;
        int failed = 0;
        List<KnowledgeBatchItemResponse> results = new ArrayList<>();
        for (MultipartFile file : files) {
            KnowledgeMutationResponse response = uploadAndIndex(file, null, null);
            results.add(new KnowledgeBatchItemResponse(
                    getOriginalFilename(file),
                    response.success(),
                    response.message()));
            if (response.success()) {
                success++;
            } else {
                failed++;
            }
        }
        return new KnowledgeBatchUploadResponse(true, files.size(), success, failed, results);
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeMutationResponse uploadAndIndex(MultipartFile file, String name, String description) {
        try {
            String filename = getOriginalFilename(file);
            String content = fileParserService.parse(file);

            if (!fileParserService.isReadableContent(content)) {
                return KnowledgeMutationResponse.failure("无法解析文件内容，请检查文件格式");
            }

            String knowledgeId = UUID.randomUUID().toString();
            String displayName = getDefaultIfBlank(name, filename);
            KnowledgeEntry entry = buildKnowledgeEntry(
                    knowledgeId,
                    displayName,
                    getDefaultIfBlank(description, DEFAULT_FILE_DESCRIPTION_PREFIX + filename),
                    SOURCE_TYPE_FILE,
                    filename);
            int chunkCount = refreshChunkMetadata(entry, content);
            knowledgeRepository.saveAndFlush(entry);
            knowledgeVectorEventPublisher.publishIndex(entry);

            LOGGER.info("Knowledge saved and indexing scheduled: {} ({} chunks, {} chars)",
                    displayName, chunkCount, content.length());

            return KnowledgeMutationResponse.success(knowledgeId, displayName, chunkCount,
                    content.length(), "知识文档已上传，向量索引任务已提交");
        } catch (Exception e) {
            LOGGER.error("Knowledge upload failed", e);
            return KnowledgeMutationResponse.failure("知识上传失败: " + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeMutationResponse addTextKnowledge(String name, String content, String description) {
        try {
            String knowledgeId = UUID.randomUUID().toString();
            String defaultName = TEXT_KNOWLEDGE_NAME_PREFIX
                    + knowledgeId.substring(0, KNOWLEDGE_ID_DISPLAY_LENGTH);
            String displayName = getDefaultIfBlank(name, defaultName);
            KnowledgeEntry entry = buildKnowledgeEntry(
                    knowledgeId,
                    displayName,
                    getDefaultIfBlank(description, DEFAULT_TEXT_DESCRIPTION),
                    SOURCE_TYPE_TEXT,
                    null);
            int chunkCount = refreshChunkMetadata(entry, content);
            knowledgeRepository.saveAndFlush(entry);
            knowledgeVectorEventPublisher.publishIndex(entry);

            LOGGER.info("Text knowledge saved and indexing scheduled: {} ({} chunks)", displayName, chunkCount);

            return KnowledgeMutationResponse.success(knowledgeId, displayName, chunkCount,
                    "文本知识已添加，向量索引任务已提交");
        } catch (Exception e) {
            LOGGER.error("Text knowledge add failed", e);
            return KnowledgeMutationResponse.failure("添加失败: " + e.getMessage());
        }
    }

    public KnowledgeListResponse listKnowledge() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<KnowledgeEntry> entries = knowledgeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<KnowledgeItemResponse> list = entries.stream()
                .map(KnowledgeItemResponse::from)
                .collect(Collectors.toList());

        return new KnowledgeListResponse(true, list, list.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeMutationResponse deleteKnowledge(String knowledgeId) {
        Optional<KnowledgeEntry> entryOptional = findCurrentTenantKnowledge(knowledgeId);
        if (entryOptional.isEmpty()) {
            return KnowledgeMutationResponse.failure("知识条目不存在");
        }
        KnowledgeEntry entry = entryOptional.get();

        try {
            knowledgeRepository.delete(entry);
            knowledgeVectorEventPublisher.publishDelete(entry);
            LOGGER.info("Knowledge deleted: {}", knowledgeId);
            return KnowledgeMutationResponse.success("知识条目已删除");
        } catch (Exception e) {
            LOGGER.error("Knowledge deletion failed", e);
            return KnowledgeMutationResponse.failure("删除失败: " + e.getMessage());
        }
    }

    public KnowledgeStatsResponse getStats() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<KnowledgeEntry> entries = knowledgeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        long totalChunks = entries.stream().mapToInt(KnowledgeEntry::getChunkCount).sum();
        long totalChars = entries.stream().mapToLong(KnowledgeEntry::getContentLength).sum();
        int vectorCount = knowledgeVectorIndexService.getIndexedCount();
        Map<String, Long> byType = knowledgeVectorIndexService.getIndexedCountByType();

        return new KnowledgeStatsResponse(true, entries.size(), totalChunks, totalChars, vectorCount, byType,
                knowledgeVectorIndexService.isUsingMilvus());
    }

    public KnowledgeDetailEnvelope getKnowledgeDetail(String knowledgeId) {
        Optional<KnowledgeEntry> entryOptional = findCurrentTenantKnowledge(knowledgeId);
        if (entryOptional.isEmpty()) {
            return KnowledgeDetailEnvelope.failure("知识条目不存在");
        }
        return KnowledgeDetailEnvelope.success(KnowledgeDetailResponse.from(entryOptional.get()));
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeMutationResponse updateKnowledge(String knowledgeId, String name, String content,
            String description) {
        Optional<KnowledgeEntry> entryOptional = findCurrentTenantKnowledge(knowledgeId);
        if (entryOptional.isEmpty()) {
            return KnowledgeMutationResponse.failure("知识条目不存在");
        }
        KnowledgeEntry entry = entryOptional.get();
        try {
            if (isNotBlank(name)) {
                entry.setName(name);
            }
            if (description != null) {
                entry.setDescription(description);
            }
            int chunkCount = refreshChunkMetadata(entry, content);

            knowledgeRepository.saveAndFlush(entry);
            knowledgeVectorEventPublisher.publishReindex(entry);
            LOGGER.info("Knowledge updated and reindexing scheduled: {} ({} chunks, {} chars)",
                    entry.getName(), chunkCount, content.length());

            return KnowledgeMutationResponse.success(knowledgeId, entry.getName(), chunkCount,
                    "知识条目已更新，向量索引任务已提交");
        } catch (Exception e) {
            LOGGER.error("Knowledge update failed", e);
            return KnowledgeMutationResponse.failure("更新失败: " + e.getMessage());
        }
    }

    public KnowledgeSearchResponse searchKnowledge(String query, int topK) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return KnowledgeSearchResponse.empty("未找到相关知识");
        }
        RagQueryAnalysis analysis = ragQueryRewriter.analyze(query);
        if (analysis.rewrittenQuery().isBlank()) {
            return KnowledgeSearchResponse.empty("未找到相关知识");
        }
        List<RetrievalResult> matches = ragReranker.rerankHybrid(
                hybridRetriever.retrieve(analysis, tenantId, topK, DEFAULT_SEARCH_MIN_SCORE,
                        List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE)),
                analysis).stream().limit(topK).toList();
        List<KnowledgeSearchResult> results = toSearchResults(matches);
        if (results.isEmpty()) {
            return KnowledgeSearchResponse.empty("未找到相关知识");
        }
        return KnowledgeSearchResponse.success(results);
    }

    private List<KnowledgeSearchResult> toSearchResults(List<RetrievalResult> matches) {
        List<KnowledgeSearchResult> results = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            results.add(toSearchResult(matches.get(i), REFERENCE_PREFIX + (i + 1)));
        }
        return results;
    }

    private KnowledgeSearchResult toSearchResult(RetrievalResult match, String referenceId) {
        return new KnowledgeSearchResult(
                referenceId,
                match.sourceType(),
                match.sourceId(),
                match.chunkId(),
                match.score(),
                match.vectorScore(),
                match.fullTextScore(),
                match.channels(),
                match.content(),
                match.sectionPath(),
                match.charStart(),
                match.charEnd(),
                match.containsTable(),
                match.containsCode(),
                match.containsList());
    }

    private KnowledgeEntry buildKnowledgeEntry(String knowledgeId, String name, String description,
            String sourceType, String sourceFilename) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setKnowledgeId(knowledgeId);
        entry.setName(name);
        entry.setDescription(description);
        entry.setSourceType(sourceType);
        entry.setSourceFilename(sourceFilename);
        entry.setTenantId(securityContextHelper.getCurrentTenantId());
        entry.setCreatedBy(securityContextHelper.getCurrentUserId());
        return entry;
    }

    private int refreshChunkMetadata(KnowledgeEntry entry, String content) {
        entry.setContent(content);
        entry.setContentLength(content.length());
        int chunkCount = knowledgeVectorIndexService.estimateChunkCount(content);
        entry.setChunkCount(chunkCount);
        return chunkCount;
    }

    private Optional<KnowledgeEntry> findCurrentTenantKnowledge(String knowledgeId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        return knowledgeRepository.findByKnowledgeIdAndTenantId(knowledgeId, tenantId);
    }

    private String getOriginalFilename(MultipartFile file) {
        return getDefaultIfBlank(file.getOriginalFilename(), UNKNOWN_FILENAME);
    }

    private String getDefaultIfBlank(String value, String defaultValue) {
        return isNotBlank(value) ? value : defaultValue;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

}
