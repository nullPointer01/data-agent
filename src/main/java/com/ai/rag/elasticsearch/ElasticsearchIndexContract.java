package com.ai.rag.elasticsearch;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexState;
import co.elastic.clients.json.JsonData;

import java.util.List;

/** 索引契约与官方 SDK 共用一份声明，旧索引存在不代表可以安全继续写入或查询。 */
public final class ElasticsearchIndexContract {
    public static final int SCHEMA_VERSION = 3;
    public static final String CHUNKING_VERSION = "cn-sentence-v1";
    public static final String ICU_PLUGIN = "analysis-icu";
    private static final List<String> KEYWORDS = List.of("tenantId", "userId", "sourceType", "sourceId",
            "chunkId", "parentChunkId");
    private static final List<String> TEXTS = List.of("title", "content", "sectionPath", "parentContext");
    private static final List<String> INTEGERS = List.of("charStart", "charEnd", "parentCharStart", "parentCharEnd");
    private static final List<String> BOOLEANS = List.of("containsTable", "containsCode", "containsList");
    private final String analyzer;

    public ElasticsearchIndexContract(String analyzer) { this.analyzer = analyzer; }

    public IndexSettings settings() {
        return IndexSettings.of(s -> s.analysis(a -> a.analyzer(analyzer,
                v -> v.custom(c -> c.tokenizer("icu_tokenizer").filter("icu_folding")))));
    }

    public TypeMapping mapping() {
        var builder = new TypeMapping.Builder().dynamic(DynamicMapping.Strict)
                .meta("ragSchemaVersion", JsonData.of(SCHEMA_VERSION))
                .meta("chunkingVersion", JsonData.of(CHUNKING_VERSION));
        KEYWORDS.forEach(f -> builder.properties(f, p -> p.keyword(k -> k)));
        TEXTS.forEach(f -> builder.properties(f, p -> p.text(t -> t.analyzer(analyzer).searchAnalyzer(analyzer))));
        INTEGERS.forEach(f -> builder.properties(f, p -> p.integer(i -> i)));
        BOOLEANS.forEach(f -> builder.properties(f, p -> p.boolean_(b -> b)));
        return builder.build();
    }

    /** ES settings 响应可能有 index 包装；映射和 analyzer 都通过 typed SDK 校验。 */
    public void validate(IndexState state) {
        require(state != null && state.mappings() != null && state.settings() != null, "缺少索引信息");
        TypeMapping actual = state.mappings();
        require(actual.dynamic() == DynamicMapping.Strict, "Mapping 必须为 strict");
        require(actual.meta().containsKey("ragSchemaVersion")
                && actual.meta().get("ragSchemaVersion").to(Integer.class) == SCHEMA_VERSION, "旧索引版本需要迁移");
        require(actual.meta().containsKey("chunkingVersion")
                && CHUNKING_VERSION.equals(actual.meta().get("chunkingVersion").to(String.class)), "分块版本不匹配");
        mapping().properties().forEach((name, expected) -> {
            var field = actual.properties().get(name);
            require(field != null && field._kind() == expected._kind(), "字段类型不匹配: " + name);
            if (expected.isText()) {
                require(analyzer.equals(field.text().analyzer()) && analyzer.equals(field.text().searchAnalyzer()),
                        "入库/查询 analyzer 不匹配: " + name);
            }
        });
        IndexSettings settings = state.settings().index() == null ? state.settings() : state.settings().index();
        require(settings.analysis() != null, "缺少 analysis 设置");
        var configured = settings.analysis().analyzer().get(analyzer);
        require(configured != null && configured.isCustom(), "缺少自定义 ICU analyzer");
        var custom = configured.custom();
        require("icu_tokenizer".equals(custom.tokenizer()) && custom.filter().equals(List.of("icu_folding"))
                && custom.charFilter().isEmpty(), "ICU analyzer 组成不匹配");
    }

    private static void require(boolean value, String detail) {
        if (!value) throw new IllegalStateException("Elasticsearch RAG 索引契约不匹配: " + detail
                + "；请在维护模式重建新索引，禁止原地修改旧 Mapping");
    }
}
