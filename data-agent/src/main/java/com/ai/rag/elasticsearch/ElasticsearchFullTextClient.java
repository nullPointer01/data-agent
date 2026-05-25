package com.ai.rag.elasticsearch;
import com.ai.rag.elasticsearch.ElasticsearchProperties;
import com.ai.rag.RagQueryAnalysis;
import com.ai.rag.fulltext.FullTextDocument;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Elasticsearch 全文检索客户端，基于官方 Java SDK。
 *
 * @author data-agent
 */
public class ElasticsearchFullTextClient {

    private static final int MAX_QUERY_TERMS = 8;

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;
    private final ElasticsearchProperties properties;

    public ElasticsearchFullTextClient(ElasticsearchClient esClient, ObjectMapper objectMapper,
            ElasticsearchProperties properties) {
        this.esClient = esClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 批量写入全文索引。
     */
    public void bulkIndex(List<FullTextDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (FullTextDocument document : documents) {
                Map<String, Object> docMap = toDocumentMap(document);
                String docId = documentId(document);
                bulkBuilder.operations(op -> op.index(idx -> idx
                        .index(properties.getIndexName())
                        .id(docId)
                        .document(docMap)));
            }
            BulkResponse response = esClient.bulk(bulkBuilder.build());
            if (response.errors()) {
                throw new IllegalStateException("Elasticsearch bulk index returned item errors");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Elasticsearch bulk index failed", e);
        }
    }

    /**
     * 按来源删除全文索引。
     */
    public void deleteBySource(String sourceType, String sourceId, String tenantId) {
        if (!StringUtils.hasText(sourceType) || !StringUtils.hasText(sourceId) || !StringUtils.hasText(tenantId)) {
            return;
        }
        try {
            DeleteByQueryRequest request = DeleteByQueryRequest.of(d -> d
                    .index(properties.getIndexName())
                    .query(q -> q.bool(b -> b
                            .filter(termQuery("sourceType", sourceType))
                            .filter(termQuery("sourceId", sourceId))
                            .filter(termQuery("tenantId", tenantId)))));
            esClient.deleteByQuery(request);
        } catch (IOException e) {
            throw new IllegalStateException("Elasticsearch delete by source failed", e);
        }
    }

    /**
     * 执行 BM25 全文检索，返回兼容旧格式的 JsonNode。
     */
    public JsonNode search(RagQueryAnalysis analysis, String tenantId, int topK, List<String> sourceTypes) {
        try {
            SearchRequest request = buildSearchRequest(analysis, tenantId, topK, sourceTypes);
            SearchResponse<Map> response = esClient.search(request, Map.class);
            return toJsonNode(response);
        } catch (IOException e) {
            throw new IllegalStateException("Elasticsearch search failed", e);
        }
    }

    /**
     * 检查 Elasticsearch 是否可访问。
     */
    public boolean isAvailable() {
        try {
            esClient.info();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private SearchRequest buildSearchRequest(RagQueryAnalysis analysis, String tenantId, int topK,
            List<String> sourceTypes) {
        List<Query> filters = new ArrayList<>();
        filters.add(termQuery("tenantId", tenantId));
        if (sourceTypes != null && !sourceTypes.isEmpty()) {
            List<FieldValue> values = sourceTypes.stream()
                    .map(FieldValue::of)
                    .toList();
            filters.add(Query.of(q -> q.terms(TermsQuery.of(t -> t
                    .field("sourceType")
                    .terms(TermsQueryField.of(tf -> tf.value(values)))))));
        }

        List<Query> shouldClauses = buildShouldClauses(analysis);

        return SearchRequest.of(s -> s
                .index(properties.getIndexName())
                .size(Math.max(1, topK))
                .query(q -> q.bool(BoolQuery.of(b -> b
                        .filter(filters)
                        .should(shouldClauses)
                        .minimumShouldMatch("1")))));
    }

    private List<Query> buildShouldClauses(RagQueryAnalysis analysis) {
        List<Query> clauses = new ArrayList<>();
        for (String term : queryTerms(analysis)) {
            clauses.add(Query.of(q -> q.multiMatch(MultiMatchQuery.of(m -> m
                    .query(term)
                    .fields("content", "title^2", "sectionPath^1.5")
                    .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)))));
        }
        return clauses;
    }

    private List<String> queryTerms(RagQueryAnalysis analysis) {
        Set<String> terms = new LinkedHashSet<>();
        if (analysis != null && StringUtils.hasText(analysis.rewrittenQuery())) {
            terms.add(analysis.rewrittenQuery().trim());
        }
        if (analysis != null && analysis.variants() != null) {
            analysis.variants().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(terms::add);
        }
        if (analysis != null && analysis.keywords() != null) {
            analysis.keywords().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(terms::add);
        }
        return terms.stream().limit(MAX_QUERY_TERMS).toList();
    }

    @SuppressWarnings("unchecked")
    private JsonNode toJsonNode(SearchResponse<Map> response) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode hitsNode = objectMapper.createObjectNode();
        ArrayNode hitsArray = objectMapper.createArrayNode();

        for (Hit<Map> hit : response.hits().hits()) {
            ObjectNode hitNode = objectMapper.createObjectNode();
            hitNode.put("_id", hit.id());
            hitNode.put("_score", hit.score() != null ? hit.score() : 0.0);
            hitNode.set("_source", objectMapper.valueToTree(hit.source()));
            hitsArray.add(hitNode);
        }
        hitsNode.set("hits", hitsArray);
        root.set("hits", hitsNode);
        return root;
    }

    private Query termQuery(String field, String value) {
        return Query.of(q -> q.term(TermQuery.of(t -> t.field(field).value(value))));
    }

    private Map<String, Object> toDocumentMap(FullTextDocument document) {
        Map<String, Object> map = new HashMap<>();
        map.put("sourceType", document.sourceType());
        map.put("sourceId", document.sourceId());
        map.put("chunkId", document.chunkId());
        map.put("tenantId", document.tenantId());
        map.put("userId", document.userId());
        map.put("title", document.title());
        map.put("content", document.content());
        map.put("sectionPath", document.metadata().sectionPath());
        map.put("charStart", document.metadata().charStart());
        map.put("charEnd", document.metadata().charEnd());
        map.put("containsTable", document.metadata().containsTable());
        map.put("containsCode", document.metadata().containsCode());
        map.put("containsList", document.metadata().containsList());
        map.put("parentChunkId", document.metadata().parentChunkId());
        map.put("parentCharStart", document.metadata().parentCharStart());
        map.put("parentCharEnd", document.metadata().parentCharEnd());
        map.put("parentContext", document.parentContext());
        return map;
    }

    private String documentId(FullTextDocument document) {
        return document.sourceType() + "::" + document.sourceId() + "::" + document.chunkId();
    }
}
