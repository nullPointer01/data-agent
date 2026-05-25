package com.ai.rag.elasticsearch;
import com.ai.rag.elasticsearch.ElasticsearchFullTextClient;
import com.ai.rag.RagQueryAnalysis;
import com.ai.rag.fulltext.FullTextSearchResult;
import com.ai.rag.fulltext.FullTextRetriever;

import com.ai.vector.ChunkMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Elasticsearch BM25 的全文检索实现。
 *
 * @author data-agent
 */
@Component
@ConditionalOnProperty(name = "app.rag.full-text-provider", havingValue = "elasticsearch")
public class ElasticsearchFullTextRetriever implements FullTextRetriever {

    private final ElasticsearchFullTextClient client;

    public ElasticsearchFullTextRetriever(ElasticsearchFullTextClient client) {
        this.client = client;
    }

    @Override
    public List<FullTextSearchResult> retrieve(RagQueryAnalysis analysis, String tenantId, int topK,
            List<String> sourceTypes) {
        if (analysis == null || !StringUtils.hasText(tenantId) || topK <= 0) {
            return List.of();
        }
        JsonNode response = client.search(analysis, tenantId, topK, sourceTypes);
        return parseHits(response);
    }

    private List<FullTextSearchResult> parseHits(JsonNode response) {
        JsonNode hits = response.path("hits").path("hits");
        if (!hits.isArray()) {
            return List.of();
        }
        List<FullTextSearchResult> results = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            results.add(new FullTextSearchResult(
                    source.path("sourceType").asText(),
                    source.path("sourceId").asText(),
                    source.path("chunkId").asText(),
                    source.path("content").asText(),
                    hit.path("_score").asDouble(),
                    metadata(source),
                    source.path("parentContext").asText("")));
        }
        return results;
    }

    private ChunkMetadata metadata(JsonNode source) {
        return new ChunkMetadata(
                source.path("sectionPath").asText(""),
                source.path("charStart").asInt(0),
                source.path("charEnd").asInt(0),
                source.path("containsTable").asBoolean(false),
                source.path("containsCode").asBoolean(false),
                source.path("containsList").asBoolean(false),
                source.path("parentChunkId").asText(""),
                source.path("parentCharStart").asInt(0),
                source.path("parentCharEnd").asInt(0));
    }
}
