package com.ai.rag;
import com.ai.rag.retrieval.RetrievalResult;

import com.ai.model.FileMetadata;
import com.ai.model.KnowledgeEntry;
import com.ai.repository.FileMetadataRepository;
import com.ai.repository.KnowledgeEntryRepository;
import com.ai.vector.VectorDocumentTypes;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * RAG 父级上下文解析器。
 *
 * @author data-agent
 */
@Component
public class RagParentContextResolver {

    private final KnowledgeEntryRepository knowledgeEntryRepository;
    private final FileMetadataRepository fileMetadataRepository;

    public RagParentContextResolver(KnowledgeEntryRepository knowledgeEntryRepository,
            FileMetadataRepository fileMetadataRepository) {
        this.knowledgeEntryRepository = knowledgeEntryRepository;
        this.fileMetadataRepository = fileMetadataRepository;
    }

    /**
     * 补齐检索结果的父级上下文。
     *
     * @param result 检索结果
     * @param tenantId 租户编号
     * @param userId 用户编号
     * @return 补齐父级上下文后的检索结果
     */
    public RetrievalResult resolve(RetrievalResult result, String tenantId, String userId) {
        if (result == null || !result.metadata().hasParent()) {
            return result;
        }
        if (StringUtils.hasText(result.parentContext())) {
            return result;
        }
        String content = findSourceContent(result, tenantId, userId).orElse("");
        String parentContext = sliceParentContext(result, content);
        if (!StringUtils.hasText(parentContext)) {
            return result;
        }
        return new RetrievalResult(result.sourceType(), result.sourceId(), result.chunkId(), result.content(),
                result.score(), result.vectorScore(), result.fullTextScore(), result.channels(), result.metadata(),
                parentContext);
    }

    /**
     * 返回当前用户可见的来源名称，供普通用户阅读引用证据。
     */
    public String resolveSourceName(RetrievalResult result, String tenantId, String userId) {
        if (result == null) {
            return "";
        }
        if (VectorDocumentTypes.KNOWLEDGE.equals(result.sourceType())) {
            return knowledgeEntryRepository
                    .findByKnowledgeIdAndTenantIdAndCreatedBy(result.sourceId(), tenantId, userId)
                    .map(KnowledgeEntry::getName)
                    .orElse("");
        }
        if (VectorDocumentTypes.FILE.equals(result.sourceType())) {
            return fileMetadataRepository
                    .findByFileIdAndTenantIdAndUploadedBy(result.sourceId(), tenantId, userId)
                    .map(FileMetadata::getFilename)
                    .orElse("");
        }
        return "";
    }

    private Optional<String> findSourceContent(RetrievalResult result, String tenantId, String userId) {
        if (VectorDocumentTypes.KNOWLEDGE.equals(result.sourceType())) {
            return knowledgeEntryRepository
                    .findByKnowledgeIdAndTenantIdAndCreatedBy(result.sourceId(), tenantId, userId)
                    .map(KnowledgeEntry::getContent);
        }
        if (VectorDocumentTypes.FILE.equals(result.sourceType())) {
            return fileMetadataRepository
                    .findByFileIdAndTenantIdAndUploadedBy(result.sourceId(), tenantId, userId)
                    .map(FileMetadata::getContent);
        }
        return Optional.empty();
    }

    private String sliceParentContext(RetrievalResult result, String sourceContent) {
        if (!StringUtils.hasText(sourceContent)) {
            return "";
        }
        int start = Math.max(0, Math.min(result.parentCharStart(), sourceContent.length()));
        int end = Math.max(start, Math.min(result.parentCharEnd(), sourceContent.length()));
        return sourceContent.substring(start, end).trim();
    }
}
