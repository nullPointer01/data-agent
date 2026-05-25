package com.ai.vector;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VectorIndexRegistryTest {

    @Test
    void recordDoesNotDoubleCountSameTenantTypeAndId() {
        VectorIndexRegistry registry = new VectorIndexRegistry(new VectorIdentityNormalizer());

        registry.record("file", "chunk-1", "hello", "pk-1", "source-1", "tenant-1", "user-1");
        registry.record("file", "chunk-1", "updated", "pk-2", "source-1", "tenant-1", "user-1");

        assertEquals(1, registry.getIndexedCount());
        assertEquals(Map.of("file", 1L), registry.getIndexedCountByType());
    }

    @Test
    void removeBySourceRemovesOnlyMatchingTenant() {
        VectorIndexRegistry registry = new VectorIndexRegistry(new VectorIdentityNormalizer());
        registry.record("file", "chunk-1", "hello", "pk-1", "source-1", "tenant-1", "user-1");
        registry.record("file", "chunk-2", "world", "pk-2", "source-1", "tenant-2", "user-2");

        int removedCount = registry.removeBySource("file", "source-1", "tenant-1");

        assertEquals(1, removedCount);
        assertEquals(1, registry.getIndexedCount());
    }

    @Test
    void removeBySourceHandlesNullSourceId() {
        VectorIndexRegistry registry = new VectorIndexRegistry(new VectorIdentityNormalizer());
        registry.record("skill", "skill-1", "hello", "pk-1", null, "tenant-1", "user-1");

        int removedCount = registry.removeBySource("skill", null, "tenant-1");

        assertEquals(1, removedCount);
        assertEquals(0, registry.getIndexedCount());
    }
}
