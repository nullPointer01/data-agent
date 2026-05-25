package com.ai.rag;
import com.ai.rag.elasticsearch.ElasticsearchProperties;
import com.ai.rag.elasticsearch.ElasticsearchFullTextClient;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * ElasticsearchFullTextClient 基础测试（SDK 版本）。
 * bulkIndex/search/deleteBySource 需要真实 ES 实例，故仅测试不依赖连接的方法。
 */
class ElasticsearchFullTextClientTest {

    @Test
    void isAvailableReturnsFalseWhenClientCannotConnect() {
        ElasticsearchFullTextClient client = new ElasticsearchFullTextClient(
                null, new com.fasterxml.jackson.databind.ObjectMapper(), properties());
        assertFalse(client.isAvailable());
    }

    @Test
    void bulkIndexSkipsEmptyList() {
        ElasticsearchFullTextClient client = new ElasticsearchFullTextClient(
                null, new com.fasterxml.jackson.databind.ObjectMapper(), properties());
        assertDoesNotThrow(() -> client.bulkIndex(List.of()));
        assertDoesNotThrow(() -> client.bulkIndex(null));
    }

    @Test
    void deleteBySourceSkipsBlankInputs() {
        ElasticsearchFullTextClient client = new ElasticsearchFullTextClient(
                null, new com.fasterxml.jackson.databind.ObjectMapper(), properties());
        assertDoesNotThrow(() -> client.deleteBySource("", "", ""));
        assertDoesNotThrow(() -> client.deleteBySource(null, null, null));
    }

    private ElasticsearchProperties properties() {
        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setBaseUrl("http://localhost:9200");
        properties.setIndexName("data-agent-rag-test");
        properties.setConnectTimeout(Duration.ofMillis(100));
        properties.setRequestTimeout(Duration.ofMillis(100));
        return properties;
    }
}
