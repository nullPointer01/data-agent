package com.ai.service.file;

import com.ai.service.VectorMemoryService;

import com.ai.rag.fulltext.FullTextIndexService;
import com.ai.vector.VectorDocumentTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 清理与已删除文件关联的向量索引。
 *
 * @author data-agent
 */
@Service
public class FileVectorCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileVectorCleanupService.class);

    private final VectorMemoryService vectorMemoryService;
    private final FullTextIndexService fullTextIndexService;

    public FileVectorCleanupService(VectorMemoryService vectorMemoryService,
            FullTextIndexService fullTextIndexService) {
        this.vectorMemoryService = vectorMemoryService;
        this.fullTextIndexService = fullTextIndexService;
    }

    /**
     * Removes file vectors. Cleanup failure is logged because metadata deletion should remain idempotent.
     *
     * @param fileId file id
     * @param tenantId tenant id
     */
    public void removeFileVectors(String fileId, String tenantId) {
        try {
            vectorMemoryService.removeFromStore(VectorDocumentTypes.FILE, fileId, tenantId);
            fullTextIndexService.deleteBySource(VectorDocumentTypes.FILE, fileId, tenantId);
        } catch (Exception e) {
            LOGGER.warn("Failed to remove file from vector store: {}", fileId, e);
        }
    }
}
