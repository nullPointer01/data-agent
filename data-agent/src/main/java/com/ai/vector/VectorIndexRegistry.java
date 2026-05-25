package com.ai.vector;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Runtime vector index statistics registry.
 *
 * @author data-agent
 */
@Component
public class VectorIndexRegistry {

    private static final int TEXT_PREVIEW_LENGTH = 100;

    private final AtomicInteger indexedCount = new AtomicInteger(0);

    private final Map<String, IndexEntry> indexRegistry = new ConcurrentHashMap<>();

    private final VectorIdentityNormalizer identityNormalizer;

    public VectorIndexRegistry(VectorIdentityNormalizer identityNormalizer) {
        this.identityNormalizer = identityNormalizer;
    }

    public void record(String type, String id, String text, String primaryKey, String sourceId,
            String tenantId, String userId) {
        String registryKey = buildRegistryKey(type, id, tenantId);
        IndexEntry previous = indexRegistry.put(registryKey,
                new IndexEntry(type, id, preview(text), primaryKey, sourceId, tenantId, userId));
        if (previous == null) {
            indexedCount.incrementAndGet();
        }
    }

    public int removeBySource(String type, String sourceId, String tenantId) {
        List<String> keysToRemove = indexRegistry.keySet().stream()
                .filter(key -> isMatchedSource(indexRegistry.get(key), type, sourceId, tenantId))
                .toList();
        keysToRemove.forEach(indexRegistry::remove);
        indexedCount.addAndGet(-keysToRemove.size());
        return keysToRemove.size();
    }

    public int getIndexedCount() {
        return indexedCount.get();
    }

    public Map<String, Long> getIndexedCountByType() {
        return indexRegistry.values().stream()
                .collect(Collectors.groupingBy(IndexEntry::type, Collectors.counting()));
    }

    private boolean isMatchedSource(IndexEntry entry, String type, String sourceId, String tenantId) {
        return entry != null
                && entry.type.equals(type)
                && Objects.equals(sourceId, entry.sourceId)
                && (!hasText(tenantId) || tenantId.equals(entry.tenantId));
    }

    private String buildRegistryKey(String type, String id, String tenantId) {
        return identityNormalizer.normalizeTenantId(tenantId) + ":" + type + ":" + id;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > TEXT_PREVIEW_LENGTH ? value.substring(0, TEXT_PREVIEW_LENGTH) : value;
    }

    private record IndexEntry(String type, String id, String text, String primaryKey, String sourceId,
            String tenantId, String userId) {
    }
}
