package com.ai.rag;
import com.ai.rag.elasticsearch.ElasticsearchFullTextClient;

import com.ai.rag.dto.RagAcceptanceCheck;
import com.ai.rag.dto.RagHealthResponse;
import com.ai.rag.dto.RagRetrievalTrace;
import com.ai.service.VectorMemoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 健康和验收状态服务。
 *
 * @author data-agent
 */
@Service
public class RagHealthService {

    private static final String PROVIDER_ELASTICSEARCH = "elasticsearch";
    private static final String VECTOR_PROVIDER = "milvus";
    private static final String CHECK_FULL_TEXT = "FULL_TEXT";
    private static final String CHECK_VECTOR = "VECTOR";
    private static final String CHECK_HYBRID = "HYBRID_RRF";
    private static final String CHECK_RERANKER = "RERANKER";
    private static final String CHECK_CITATION = "CITATION";
    private static final String CHECK_ES_LATENCY = "ES_LATENCY";
    private static final String CHECK_MILVUS_LATENCY = "MILVUS_LATENCY";
    private static final String CHECK_RAG_LATENCY = "RAG_LATENCY";

    private final RagProperties ragProperties;
    private final VectorMemoryService vectorMemoryService;
    private final EnhancedRagPipeline enhancedRagPipeline;
    private final ObjectProvider<ElasticsearchFullTextClient> elasticsearchClientProvider;

    public RagHealthService(RagProperties ragProperties,
            VectorMemoryService vectorMemoryService,
            EnhancedRagPipeline enhancedRagPipeline,
            ObjectProvider<ElasticsearchFullTextClient> elasticsearchClientProvider) {
        this.ragProperties = ragProperties;
        this.vectorMemoryService = vectorMemoryService;
        this.enhancedRagPipeline = enhancedRagPipeline;
        this.elasticsearchClientProvider = elasticsearchClientProvider;
    }

    /**
     * 构建 RAG 健康状态。
     *
     * @return RAG 健康状态
     */
    public RagHealthResponse getHealth() {
        RagRetrievalTrace lastTrace = enhancedRagPipeline.getLastTrace();
        boolean vectorHealthy = vectorMemoryService.isVectorStoreAvailable();
        boolean fullTextHealthy = isFullTextHealthy();
        List<RagAcceptanceCheck> checks = checks(lastTrace, vectorHealthy, fullTextHealthy);
        List<String> issues = issues(checks);
        return new RagHealthResponse(ragProperties.isEnabled(), issues.isEmpty(), fullTextProvider(),
                fullTextHealthy, VECTOR_PROVIDER, vectorHealthy, fullTextHealthy && vectorHealthy, true, true,
                ragProperties.getHealth().getEsLatencyThresholdMs(),
                ragProperties.getHealth().getMilvusLatencyThresholdMs(),
                ragProperties.getHealth().getRagLatencyThresholdMs(), lastTrace, checks, issues);
    }

    private boolean isFullTextHealthy() {
        ElasticsearchFullTextClient client = elasticsearchClientProvider.getIfAvailable();
        return client != null && client.isAvailable();
    }

    private List<RagAcceptanceCheck> checks(RagRetrievalTrace trace, boolean vectorHealthy,
            boolean fullTextHealthy) {
        List<RagAcceptanceCheck> checks = new ArrayList<>();
        checks.add(check(CHECK_FULL_TEXT, "全文检索", fullTextHealthy, fullTextDetail(fullTextHealthy)));
        checks.add(check(CHECK_VECTOR, "Milvus 向量检索", vectorHealthy, vectorHealthy ? "Milvus 客户端已初始化" : "Milvus 客户端不可用"));
        boolean hybridReady = fullTextHealthy && vectorHealthy;
        checks.add(check(CHECK_HYBRID, "RRF 混合检索", hybridReady,
                hybridReady ? "Milvus 与 Elasticsearch 均可用，可执行 RRF 融合" : "混合检索依赖未全部就绪"));
        checks.add(check(CHECK_RERANKER, "重排序", true, "轻量重排器已接入关键词覆盖率和来源权重"));
        checks.add(check(CHECK_CITATION, "引用溯源", true, "引用返回来源、位置、通道分数和结构标签"));
        checks.add(checkEsLatency(trace));
        checks.add(checkLatency(CHECK_MILVUS_LATENCY, "Milvus 检索延迟", trace.vectorRetrievalTimeMs(),
                ragProperties.getHealth().getMilvusLatencyThresholdMs(), trace.vectorRawCount() > 0));
        checks.add(checkLatency(CHECK_RAG_LATENCY, "RAG 端到端延迟", trace.totalTimeMs(),
                ragProperties.getHealth().getRagLatencyThresholdMs(), trace.enabled()));
        return checks;
    }

    private RagAcceptanceCheck checkLatency(String key, String name, long actualMs, long thresholdMs,
            boolean hasSample) {
        if (!hasSample) {
            return check(key, name, false, "暂无检索样本，执行一次 RAG 检索后可验收");
        }
        boolean passed = actualMs <= thresholdMs;
        return check(key, name, passed, "当前 " + actualMs + " ms，阈值 " + thresholdMs + " ms");
    }

    private RagAcceptanceCheck checkEsLatency(RagRetrievalTrace trace) {
        return checkLatency(CHECK_ES_LATENCY, "ES 检索延迟", trace.fullTextRetrievalTimeMs(),
                ragProperties.getHealth().getEsLatencyThresholdMs(), usesElasticsearch(trace));
    }

    private boolean usesElasticsearch(RagRetrievalTrace trace) {
        return trace.fullTextRawCount() > 0;
    }

    private RagAcceptanceCheck check(String key, String name, boolean passed, String detail) {
        return new RagAcceptanceCheck(key, name, passed, detail);
    }

    private String fullTextDetail(boolean fullTextHealthy) {
        return fullTextHealthy ? "Elasticsearch 可访问" : "Elasticsearch 不可访问，RAG 不执行 SQL 模糊检索降级";
    }

    private List<String> issues(List<RagAcceptanceCheck> checks) {
        return checks.stream()
                .filter(check -> !check.passed())
                .map(check -> check.name() + "未通过：" + check.detail())
                .toList();
    }

    private String fullTextProvider() {
        return PROVIDER_ELASTICSEARCH;
    }
}
