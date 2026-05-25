package com.ai.vector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MilvusDeleteExpressionBuilderTest {

    @Test
    void buildBySourceIncludesTenantWhenProvided() {
        MilvusDeleteExpressionBuilder builder = new MilvusDeleteExpressionBuilder();

        String expression = builder.buildBySource("file", "source-1", "tenant-1");

        assertEquals("metadata[\"type\"] == \"file\" and metadata[\"sourceId\"] == \"source-1\" "
                + "and metadata[\"tenantId\"] == \"tenant-1\"", expression);
    }

    @Test
    void buildBySourceEscapesQuotesAndBackslashes() {
        MilvusDeleteExpressionBuilder builder = new MilvusDeleteExpressionBuilder();

        String expression = builder.buildBySource("knowledge", "a\"b\\c", null);

        assertEquals("metadata[\"type\"] == \"knowledge\" and metadata[\"sourceId\"] == \"a\\\"b\\\\c\"",
                expression);
    }
}
