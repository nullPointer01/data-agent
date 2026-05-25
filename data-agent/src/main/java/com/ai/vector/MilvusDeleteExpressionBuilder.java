package com.ai.vector;

import org.springframework.stereotype.Component;

/**
 * Builds Milvus boolean expressions for metadata-scoped vector deletion.
 *
 * @author data-agent
 */
@Component
public class MilvusDeleteExpressionBuilder {

    /**
     * Builds a delete expression for all chunks under one source.
     *
     * @param type vector document type
     * @param sourceId source id
     * @param tenantId optional tenant id
     * @return Milvus delete expression
     */
    public String buildBySource(String type, String sourceId, String tenantId) {
        String expression = jsonMetadataEquals(VectorMetadataKeys.TYPE, type)
                + " and " + jsonMetadataEquals(VectorMetadataKeys.SOURCE_ID, sourceId);
        if (hasText(tenantId)) {
            expression += " and " + jsonMetadataEquals(VectorMetadataKeys.TENANT_ID, tenantId);
        }
        return expression;
    }

    private String jsonMetadataEquals(String key, String value) {
        return "metadata[\"" + escapeMilvusString(key) + "\"] == \"" + escapeMilvusString(value) + "\"";
    }

    private String escapeMilvusString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
