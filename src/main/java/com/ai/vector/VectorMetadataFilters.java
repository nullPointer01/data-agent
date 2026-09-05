package com.ai.vector;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;

import java.util.Collection;

/**
 * 向量检索元数据过滤器构建工具。
 *
 * @author data-agent
 */
public final class VectorMetadataFilters {

    private VectorMetadataFilters() {
    }

    public static Filter searchFilter(String tenantId, Collection<String> sourceTypes) {
        return searchFilter(tenantId, null, sourceTypes);
    }

    public static Filter searchFilter(String tenantId, String userId, Collection<String> sourceTypes) {
        Filter filter = null;
        if (hasText(tenantId)) {
            filter = MetadataFilterBuilder.metadataKey(VectorMetadataKeys.TENANT_ID).isEqualTo(tenantId);
        }
        if (hasText(userId)) {
            Filter userFilter = MetadataFilterBuilder.metadataKey(VectorMetadataKeys.USER_ID).isEqualTo(userId);
            filter = filter == null ? userFilter : filter.and(userFilter);
        }
        if (sourceTypes != null && !sourceTypes.isEmpty()) {
            Filter typeFilter = MetadataFilterBuilder.metadataKey(VectorMetadataKeys.TYPE).isIn(sourceTypes);
            filter = filter == null ? typeFilter : filter.and(typeFilter);
        }
        return filter;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
