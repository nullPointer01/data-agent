package com.ai.resource;

import com.ai.model.FileMetadata;
import com.ai.model.FileProcessingStatus;
import com.ai.model.KnowledgeEntry;
import com.ai.repository.FileMetadataRepository;
import com.ai.repository.KnowledgeEntryRepository;
import com.ai.resource.dto.ResourceAssetResponse;
import com.ai.resource.dto.ResourceAssetsResponse;
import com.ai.resource.dto.ResourceSummaryResponse;
import com.ai.security.SecurityContextHelper;
import com.ai.service.VectorMemoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Aggregates files and knowledge entries for the resource center.
 *
 * @author data-agent
 */
@Service
public class ResourceCenterService {

    private static final String RESOURCE_TYPE_FILE = "FILE";

    private static final String RESOURCE_TYPE_KNOWLEDGE = "KNOWLEDGE";

    private static final String STATUS_INDEXED = "INDEXED";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final FileMetadataRepository fileMetadataRepository;

    private final KnowledgeEntryRepository knowledgeEntryRepository;

    private final SecurityContextHelper securityContextHelper;

    private final VectorMemoryService vectorMemoryService;

    public ResourceCenterService(FileMetadataRepository fileMetadataRepository,
            KnowledgeEntryRepository knowledgeEntryRepository,
            SecurityContextHelper securityContextHelper,
            VectorMemoryService vectorMemoryService) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.knowledgeEntryRepository = knowledgeEntryRepository;
        this.securityContextHelper = securityContextHelper;
        this.vectorMemoryService = vectorMemoryService;
    }

    public ResourceAssetsResponse listAssets(String keyword, String resourceType, String status) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<FileMetadata> files = fileMetadataRepository.findByTenantId(tenantId);
        List<KnowledgeEntry> knowledgeEntries = knowledgeEntryRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        List<ResourceAssetResponse> assets = new ArrayList<>();
        files.stream().map(this::convertFile).forEach(assets::add);
        knowledgeEntries.stream().map(this::convertKnowledge).forEach(assets::add);

        List<ResourceAssetResponse> filteredAssets = assets.stream()
                .filter(asset -> matchesKeyword(asset, keyword))
                .filter(asset -> matchesIgnoreCase(asset.getResourceType(), resourceType))
                .filter(asset -> matchesIgnoreCase(asset.getStatus(), status))
                .sorted(Comparator.comparing(ResourceAssetResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        ResourceAssetsResponse response = new ResourceAssetsResponse();
        response.setSummary(buildSummary(files, knowledgeEntries));
        response.setAssets(filteredAssets);
        return response;
    }

    public ResourceSummaryResponse getSummary() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<FileMetadata> files = fileMetadataRepository.findByTenantId(tenantId);
        List<KnowledgeEntry> knowledgeEntries = knowledgeEntryRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return buildSummary(files, knowledgeEntries);
    }

    private ResourceSummaryResponse buildSummary(List<FileMetadata> files, List<KnowledgeEntry> knowledgeEntries) {
        ResourceSummaryResponse summary = new ResourceSummaryResponse();
        summary.setFileCount(files.size());
        summary.setKnowledgeCount(knowledgeEntries.size());
        summary.setTotalAssets(files.size() + knowledgeEntries.size());
        summary.setIndexedChunkCount(knowledgeEntries.stream().mapToLong(KnowledgeEntry::getChunkCount).sum());
        summary.setTotalStorageBytes(files.stream().mapToLong(FileMetadata::getSize).sum()
                + knowledgeEntries.stream().mapToLong(KnowledgeEntry::getContentLength).sum());
        summary.setFailedFileCount(files.stream()
                .filter(file -> FileProcessingStatus.FAILED.equals(file.getProcessingStatus()))
                .count());
        summary.setUsingMilvus(vectorMemoryService.isUsingMilvus());
        return summary;
    }

    private ResourceAssetResponse convertFile(FileMetadata fileMetadata) {
        ResourceAssetResponse response = new ResourceAssetResponse();
        response.setId(fileMetadata.getFileId());
        response.setName(fileMetadata.getFilename());
        response.setResourceType(RESOURCE_TYPE_FILE);
        response.setSourceType("upload");
        response.setStatus(fileMetadata.getProcessingStatus() != null ? fileMetadata.getProcessingStatus().name() : "");
        response.setContentType(fileMetadata.getContentType());
        response.setSize(fileMetadata.getSize());
        response.setContentLength(fileMetadata.getContent() != null ? fileMetadata.getContent().length() : 0);
        response.setCreatedAt(formatLocalDateTime(fileMetadata.getUploadedAt()));
        response.setUpdatedAt(formatLocalDateTime(fileMetadata.getProcessedAt()));
        response.setErrorMessage(fileMetadata.getProcessingError());
        return response;
    }

    private ResourceAssetResponse convertKnowledge(KnowledgeEntry knowledgeEntry) {
        ResourceAssetResponse response = new ResourceAssetResponse();
        response.setId(knowledgeEntry.getKnowledgeId());
        response.setName(knowledgeEntry.getName());
        response.setResourceType(RESOURCE_TYPE_KNOWLEDGE);
        response.setSourceType(knowledgeEntry.getSourceType());
        response.setStatus(STATUS_INDEXED);
        response.setDescription(knowledgeEntry.getDescription());
        response.setSourceFilename(knowledgeEntry.getSourceFilename());
        response.setContentLength(knowledgeEntry.getContentLength());
        response.setChunkCount(knowledgeEntry.getChunkCount());
        response.setCreatedAt(formatDate(knowledgeEntry.getCreatedAt()));
        response.setUpdatedAt(formatDate(knowledgeEntry.getUpdatedAt()));
        return response;
    }

    private boolean matchesKeyword(ResourceAssetResponse asset, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        return contains(asset.getName(), normalizedKeyword)
                || contains(asset.getDescription(), normalizedKeyword)
                || contains(asset.getSourceFilename(), normalizedKeyword)
                || contains(asset.getContentType(), normalizedKeyword);
    }

    private boolean contains(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private boolean matchesIgnoreCase(String value, String expected) {
        return expected == null || expected.isBlank() || expected.equalsIgnoreCase(value);
    }

    private String formatLocalDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    private String formatDate(Date value) {
        if (value == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault()));
    }
}
