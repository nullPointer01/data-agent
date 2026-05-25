package com.ai.resource.dto;

/**
 * Resource center summary response.
 *
 * @author data-agent
 */
public class ResourceSummaryResponse {

    private long totalAssets;

    private long fileCount;

    private long knowledgeCount;

    private long indexedChunkCount;

    private long totalStorageBytes;

    private long failedFileCount;

    private boolean usingMilvus;

    public long getTotalAssets() {
        return totalAssets;
    }

    public void setTotalAssets(long totalAssets) {
        this.totalAssets = totalAssets;
    }

    public long getFileCount() {
        return fileCount;
    }

    public void setFileCount(long fileCount) {
        this.fileCount = fileCount;
    }

    public long getKnowledgeCount() {
        return knowledgeCount;
    }

    public void setKnowledgeCount(long knowledgeCount) {
        this.knowledgeCount = knowledgeCount;
    }

    public long getIndexedChunkCount() {
        return indexedChunkCount;
    }

    public void setIndexedChunkCount(long indexedChunkCount) {
        this.indexedChunkCount = indexedChunkCount;
    }

    public long getTotalStorageBytes() {
        return totalStorageBytes;
    }

    public void setTotalStorageBytes(long totalStorageBytes) {
        this.totalStorageBytes = totalStorageBytes;
    }

    public long getFailedFileCount() {
        return failedFileCount;
    }

    public void setFailedFileCount(long failedFileCount) {
        this.failedFileCount = failedFileCount;
    }

    public boolean isUsingMilvus() {
        return usingMilvus;
    }

    public void setUsingMilvus(boolean usingMilvus) {
        this.usingMilvus = usingMilvus;
    }
}
