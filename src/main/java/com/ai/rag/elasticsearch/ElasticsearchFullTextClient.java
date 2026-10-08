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
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
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
import java.util.stream.Collectors;

/**
 * Elasticsearch 全文检索客户端，基于官方 Java SDK。
 *
 * @author data-agent
 */
public class ElasticsearchFullTextClient {

    private static final int MAX_QUERY_TERMS = 8;
    private static final int MAX_BULK_ERRORS_IN_MESSAGE = 3;
    private static final String FIELD_SOURCE_TYPE = "sourceType";
    private static final String FIELD_SOURCE_ID = "sourceId";
    private static final String FIELD_CHUNK_ID = "chunkId";
    private static final String FIELD_TENANT_ID = "tenantId";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_SECTION_PATH = "sectionPath";
    private static final String FIELD_CHAR_START = "charStart";
    private static final String FIELD_CHAR_END = "charEnd";
    private static final String FIELD_CONTAINS_TABLE = "containsTable";
    private static final String FIELD_CONTAINS_CODE = "containsCode";
    private static final String FIELD_CONTAINS_LIST = "containsList";
    private static final String FIELD_PARENT_CHUNK_ID = "parentChunkId";
    private static final String FIELD_PARENT_CHAR_START = "parentCharStart";
    private static final String FIELD_PARENT_CHAR_END = "parentCharEnd";
    private static final String FIELD_PARENT_CONTEXT = "parentContext";

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;
    private final ElasticsearchProperties properties;
    private final Object indexInitializationMonitor = new Object();

    private volatile long contractValidUntil;

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
        ensureIndex();
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
                String errorSummary = response.items().stream()
                        .filter(item -> item.error() != null)
                        .limit(MAX_BULK_ERRORS_IN_MESSAGE)
                        .map(item -> item.id() + ": " + item.error().reason())
                        .collect(Collectors.joining("; "));
                throw new IllegalStateException("Elasticsearch bulk index returned item errors: " + errorSummary);
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
        ensureIndex();
        try {
            DeleteByQueryRequest request = DeleteByQueryRequest.of(d -> d
                    .index(properties.getIndexName())
                    .query(q -> q.bool(b -> b
                            .filter(termQuery(FIELD_SOURCE_TYPE, sourceType))
                            .filter(termQuery(FIELD_SOURCE_ID, sourceId))
                            .filter(termQuery(FIELD_TENANT_ID, tenantId)))));
            DeleteByQueryResponse response = esClient.deleteByQuery(request);
            if (Boolean.TRUE.equals(response.timedOut())) {
                throw new IllegalStateException("Elasticsearch delete by source timed out");
            }
            if (!response.failures().isEmpty()) {
                String failureSummary = response.failures().stream()
                        .limit(MAX_BULK_ERRORS_IN_MESSAGE)
                        .map(failure -> failure.id() + ": " + failure.cause().reason())
                        .collect(Collectors.joining("; "));
                throw new IllegalStateException("Elasticsearch delete by source returned failures: "
                        + failureSummary);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Elasticsearch delete by source failed", e);
        }
    }

    /**
     * 执行 BM25 全文检索，返回兼容旧格式的 JsonNode。
     */
    public JsonNode search(RagQueryAnalysis analysis, String tenantId, String userId, int topK,
            List<String> sourceTypes) {
        ensureIndex();
        try {
            SearchRequest request = buildSearchRequest(analysis, tenantId, userId, topK, sourceTypes);
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
            ensureIndex();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private SearchRequest buildSearchRequest(RagQueryAnalysis analysis, String tenantId, String userId, int topK,
            List<String> sourceTypes) {
        List<Query> filters = new ArrayList<>();
        filters.add(termQuery(FIELD_TENANT_ID, tenantId));
        filters.add(termQuery(FIELD_USER_ID, userId));
        if (sourceTypes != null && !sourceTypes.isEmpty()) {
            List<FieldValue> values = sourceTypes.stream()
                    .map(FieldValue::of)
                    .toList();
            filters.add(Query.of(q -> q.terms(TermsQuery.of(t -> t
                    .field(FIELD_SOURCE_TYPE)
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
                    .fields(FIELD_CONTENT, FIELD_TITLE + "^2", FIELD_SECTION_PATH + "^1.5")
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

    /**
     * 首次使用时幂等创建具有固定字段类型的索引，避免动态 Mapping 让精确过滤字段变成 text。
     */
    private void ensureIndex() {
        if (System.nanoTime() < contractValidUntil) {
            return;
        }
        synchronized (indexInitializationMonitor) {
            if (System.nanoTime() < contractValidUntil) {
                return;
            }
            try {
                requireIcuPlugin();
                if (!indexExists()) {
                    try {
                        createIndex();
                    } catch (ElasticsearchException concurrentCreation) {
                        if (!"resource_already_exists_exception".equals(concurrentCreation.error().type())) {
                            throw concurrentCreation;
                        }
                        // 只有同名并发创建可以接续，接下来仍必须检查实际索引契约。
                    }
                }
                var state = esClient.indices().get(g -> g.index(properties.getIndexName())).result();
                if (state.size() != 1 || !state.containsKey(properties.getIndexName())) {
                    throw new IllegalStateException("RAG 必须配置单一物理索引，不能使用通配符或跨索引别名");
                }
                new ElasticsearchIndexContract(properties.getAnalyzer()).validate(state.get(properties.getIndexName()));
                contractValidUntil = System.nanoTime() + properties.getContractCheckInterval().toNanos();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Elasticsearch RAG index contract check failed: " + properties.getIndexName(), e);
            }
        }
    }

    private boolean indexExists() throws IOException {
        return esClient.indices().exists(request -> request.index(properties.getIndexName())).value();
    }

    private void requireIcuPlugin() throws IOException {
        var nodes = esClient.nodes().info(n -> n.metric("plugins")).nodes();
        if (nodes.isEmpty() || nodes.values().stream().anyMatch(node -> node.plugins().stream()
                .noneMatch(plugin -> ElasticsearchIndexContract.ICU_PLUGIN.equals(plugin.name())))) {
            throw new IllegalStateException("Elasticsearch 节点缺少 analysis-icu；请重建 8.12.0-icu 镜像");
        }
    }

    private void createIndex() throws IOException {
        var contract = new ElasticsearchIndexContract(properties.getAnalyzer());
        esClient.indices().create(request -> request
                .index(properties.getIndexName())
                .settings(contract.settings()).mappings(contract.mapping()));
    }

    private Map<String, Object> toDocumentMap(FullTextDocument document) {
        Map<String, Object> map = new HashMap<>();
        map.put(FIELD_SOURCE_TYPE, document.sourceType());
        map.put(FIELD_SOURCE_ID, document.sourceId());
        map.put(FIELD_CHUNK_ID, document.chunkId());
        map.put(FIELD_TENANT_ID, document.tenantId());
        map.put(FIELD_USER_ID, document.userId());
        map.put(FIELD_TITLE, document.title());
        map.put(FIELD_CONTENT, document.content());
        map.put(FIELD_SECTION_PATH, document.metadata().sectionPath());
        map.put(FIELD_CHAR_START, document.metadata().charStart());
        map.put(FIELD_CHAR_END, document.metadata().charEnd());
        map.put(FIELD_CONTAINS_TABLE, document.metadata().containsTable());
        map.put(FIELD_CONTAINS_CODE, document.metadata().containsCode());
        map.put(FIELD_CONTAINS_LIST, document.metadata().containsList());
        map.put(FIELD_PARENT_CHUNK_ID, document.metadata().parentChunkId());
        map.put(FIELD_PARENT_CHAR_START, document.metadata().parentCharStart());
        map.put(FIELD_PARENT_CHAR_END, document.metadata().parentCharEnd());
        map.put(FIELD_PARENT_CONTEXT, document.parentContext());
        return map;
    }

    private String documentId(FullTextDocument document) {
        // v3 物理 ID 包含身份，避免相同来源/块编号跨用户覆盖；字段仍保留原业务 ID。
        try {
            String identity = String.join("\0", document.tenantId(), document.userId(), document.sourceType(),
                    document.sourceId(), document.chunkId());
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
