package com.ai.vector;

import org.springframework.stereotype.Component;

/**
 * 为元数据范围的向量删除构建 Milvus 布尔表达式。
 *
 * @author data-agent
 */
@Component
public class MilvusDeleteExpressionBuilder {

    /**
     * 为一个来源下的所有块构建删除表达式。
     *
     * @param type 向量文档类型
     * @param sourceId 来源 ID
     * @param tenantId 可选的租户 ID
     * @return Milvus 删除表达式
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
