package com.ai.rag.eval;

import com.ai.rag.RagProperties;
import com.ai.rag.RagQueryAnalysis;
import com.ai.rag.RagQueryRewriter;
import com.ai.rag.RagReranker;
import com.ai.rag.RagReranker.RerankOutcome;
import com.ai.rag.RagHealthService;
import com.ai.rag.dto.RagHealthResponse;
import com.ai.rag.eval.RagBenchmarkDatasetLoader.LoadedDataset;
import com.ai.rag.eval.RagBenchmarkReport.CaseResult;
import com.ai.rag.eval.RagBenchmarkReport.ConfigurationSnapshot;
import com.ai.rag.eval.RagBenchmarkReport.DatasetSnapshot;
import com.ai.rag.eval.RagBenchmarkReport.QualityGate;
import com.ai.rag.eval.RagBenchmarkReport.RankedHit;
import com.ai.rag.eval.RagBenchmarkReport.RankingMetrics;
import com.ai.rag.eval.RagBenchmarkReport.StageResult;
import com.ai.rag.eval.RagBenchmarkReport.StageSummary;
import com.ai.rag.fulltext.FullTextSearchResult;
import com.ai.rag.retrieval.HybridRetrievalResult;
import com.ai.rag.retrieval.HybridRetriever;
import com.ai.rag.retrieval.RetrievalChannel;
import com.ai.rag.retrieval.RetrievalResult;
import com.ai.security.SecurityContextHelper;
import com.ai.vector.EmbeddingGateway;
import com.ai.vector.EmbeddingProfile;
import com.ai.vector.VectorDocumentTypes;
import com.ai.vector.VectorMetadataKeys;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用同一批候选对 Vector、BM25、RRF 和当前精排 Provider 进行批量评测。
 *
 * @author data-agent
 */
@Service
public class RagBenchmarkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagBenchmarkService.class);
    private static final String STAGE_VECTOR = "VECTOR";
    private static final String STAGE_BM25 = "BM25";
    private static final String STAGE_RRF = "RRF";
    private static final String STAGE_RERANK = "RERANK";
    private static final List<String> STAGE_ORDER = List.of(
            STAGE_VECTOR, STAGE_BM25, STAGE_RRF, STAGE_RERANK);
    private static final List<String> RAG_SOURCE_TYPES = List.of(
            VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE);
    private static final double RECALL_AT_20_TARGET = 0.90D;
    private static final double MRR_AT_10_TARGET = 0.75D;
    private static final int MINIMUM_CANDIDATE_TOP_K = 20;

    private final RagBenchmarkDatasetLoader datasetLoader;
    private final RagRankingMetrics rankingMetrics;
    private final RagQueryRewriter queryRewriter;
    private final HybridRetriever hybridRetriever;
    private final RagReranker ragReranker;
    private final RagProperties ragProperties;
    private final EmbeddingGateway embeddingGateway;
    private final SecurityContextHelper securityContextHelper;
    private final RagHealthService ragHealthService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RagBenchmarkService(RagBenchmarkDatasetLoader datasetLoader,
            RagRankingMetrics rankingMetrics,
            RagQueryRewriter queryRewriter,
            HybridRetriever hybridRetriever,
            RagReranker ragReranker,
            RagProperties ragProperties,
            EmbeddingGateway embeddingGateway,
            SecurityContextHelper securityContextHelper,
            RagHealthService ragHealthService) {
        this.datasetLoader = datasetLoader;
        this.rankingMetrics = rankingMetrics;
        this.queryRewriter = queryRewriter;
        this.hybridRetriever = hybridRetriever;
        this.ragReranker = ragReranker;
        this.ragProperties = ragProperties;
        this.embeddingGateway = embeddingGateway;
        this.securityContextHelper = securityContextHelper;
        this.ragHealthService = ragHealthService;
    }

    /**
     * 对当前管理员所属租户运行固定黄金集。
     *
     * @return 分阶段批量评测报告
     */
    public RagBenchmarkReport runCurrentTenant() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("RAG批量评测正在运行，请勿重复提交");
        }
        try {
            return executeCurrentTenant();
        } finally {
            running.set(false);
        }
    }

    private RagBenchmarkReport executeCurrentTenant() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalStateException("无法运行RAG评测：当前认证用户缺少tenantId");
        }
        if (ragProperties.getCandidateTopK() < MINIMUM_CANDIDATE_TOP_K) {
            throw new IllegalStateException("无法计算Recall@20：candidateTopK必须至少为20，实际="
                    + ragProperties.getCandidateTopK());
        }
        RagHealthResponse health = ragHealthService.getHealth();
        if (!health.hybridRetrievalReady()) {
            throw new IllegalStateException("无法运行RAG评测：Milvus或Elasticsearch未就绪");
        }
        Instant startedAt = Instant.now();
        LoadedDataset loadedDataset = datasetLoader.load();
        List<CaseResult> caseResults = loadedDataset.dataset().cases().stream()
                .map(benchmarkCase -> evaluateCase(benchmarkCase, tenantId))
                .toList();
        List<StageSummary> summaries = summarize(caseResults);
        List<String> failedCaseIds = caseResults.stream()
                .filter(result -> StringUtils.hasText(result.failureStage()))
                .map(CaseResult::caseId)
                .toList();
        return new RagBenchmarkReport(
                UUID.randomUUID().toString(),
                startedAt,
                Instant.now(),
                datasetSnapshot(loadedDataset),
                configurationSnapshot(),
                summaries,
                caseResults,
                failedCaseIds,
                qualityGate(summaries));
    }

    private CaseResult evaluateCase(RagBenchmarkCase benchmarkCase, String tenantId) {
        try {
            RagQueryAnalysis analysis = queryRewriter.analyze(benchmarkCase.query());
            HybridRetrievalResult hybrid = hybridRetriever.retrieveWithTrace(
                    analysis,
                    tenantId,
                    ragProperties.getCandidateTopK(),
                    ragProperties.getMinScore(),
                    RAG_SOURCE_TYPES);
            List<RankedHit> vectorHits = vectorHits(hybrid.vectorResults());
            List<RankedHit> bm25Hits = fullTextHits(hybrid.fullTextResults());
            List<RankedHit> rrfHits = retrievalHits(hybrid.results());
            long rerankStartedAt = System.currentTimeMillis();
            RerankOutcome rerankOutcome = ragReranker.rerankHybridWithTrace(hybrid.results(), analysis);
            long rerankTimeMs = System.currentTimeMillis() - rerankStartedAt;
            List<RankedHit> rerankedHits = rerankedHits(rerankOutcome);
            long retrievalTimeMs = hybrid.vectorRetrievalTimeMs()
                    + hybrid.fullTextRetrievalTimeMs()
                    + hybrid.fusionTimeMs();
            List<StageResult> stages = List.of(
                    stage(STAGE_VECTOR, benchmarkCase, vectorHits, hybrid.vectorRetrievalTimeMs()),
                    stage(STAGE_BM25, benchmarkCase, bm25Hits, hybrid.fullTextRetrievalTimeMs()),
                    stage(STAGE_RRF, benchmarkCase, rrfHits, retrievalTimeMs),
                    stage(STAGE_RERANK, benchmarkCase, rerankedHits,
                            retrievalTimeMs + rerankTimeMs));
            return new CaseResult(
                    benchmarkCase.caseId(),
                    benchmarkCase.query(),
                    safeList(benchmarkCase.tags()),
                    failureStage(stages),
                    "",
                    rerankOutcome.evidence().provider(),
                    rerankOutcome.evidence().model(),
                    rerankOutcome.evidence().fallbackProvider(),
                    rerankOutcome.evidence().fallback(),
                    rerankOutcome.evidence().errorCategory(),
                    stages);
        } catch (RuntimeException e) {
            LOGGER.warn("RAG benchmark case执行失败: caseId={}, errorType={}", benchmarkCase.caseId(),
                    e.getClass().getSimpleName(), e);
            return failedCase(benchmarkCase, e.getClass().getSimpleName());
        }
    }

    private CaseResult failedCase(RagBenchmarkCase benchmarkCase, String errorType) {
        List<StageResult> stages = STAGE_ORDER.stream()
                .map(stage -> stage(stage, benchmarkCase, List.of(), -1L))
                .toList();
        return new CaseResult(benchmarkCase.caseId(), benchmarkCase.query(), safeList(benchmarkCase.tags()),
                "EXECUTION_ERROR", errorType, "", "", "", false, "", stages);
    }

    private StageResult stage(String stage, RagBenchmarkCase benchmarkCase, List<RankedHit> hits, long latencyMs) {
        return new StageResult(stage, rankingMetrics.evaluate(benchmarkCase, hits), latencyMs, hits);
    }

    private String failureStage(List<StageResult> stages) {
        StageResult vector = findStage(stages, STAGE_VECTOR);
        StageResult bm25 = findStage(stages, STAGE_BM25);
        StageResult rrf = findStage(stages, STAGE_RRF);
        StageResult reranked = findStage(stages, STAGE_RERANK);
        if (notFound(vector) && notFound(bm25)) {
            return "RECALL";
        }
        if (notFound(rrf)) {
            return "FUSION";
        }
        if (notFound(reranked)) {
            return "RERANK";
        }
        if (reranked.metrics().firstRelevantRank() > ragProperties.getTopK()) {
            return rrf.metrics().firstRelevantRank() <= ragProperties.getTopK()
                    ? "RERANK_HARM"
                    : "FINAL_TOP_K";
        }
        return "";
    }

    private boolean notFound(StageResult result) {
        return result.metrics().firstRelevantRank() <= 0;
    }

    private StageResult findStage(List<StageResult> stages, String stageName) {
        return stages.stream()
                .filter(stage -> stageName.equals(stage.stage()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("RAG评测阶段缺失: " + stageName));
    }

    private List<RankedHit> vectorHits(List<EmbeddingMatch<TextSegment>> matches) {
        List<RankedHit> hits = new ArrayList<>();
        List<EmbeddingMatch<TextSegment>> safeMatches = safeList(matches);
        for (int index = 0; index < safeMatches.size(); index++) {
            EmbeddingMatch<TextSegment> match = safeMatches.get(index);
            TextSegment segment = match.embedded();
            Metadata metadata = segment == null ? null : segment.metadata();
            hits.add(new RankedHit(index + 1,
                    metadataValue(metadata, VectorMetadataKeys.TYPE),
                    metadataValue(metadata, VectorMetadataKeys.SOURCE_ID),
                    metadataValue(metadata, VectorMetadataKeys.ID),
                    match.score(),
                    List.of(RetrievalChannel.VECTOR)));
        }
        return List.copyOf(hits);
    }

    private List<RankedHit> fullTextHits(List<FullTextSearchResult> matches) {
        List<RankedHit> hits = new ArrayList<>();
        List<FullTextSearchResult> safeMatches = safeList(matches);
        for (int index = 0; index < safeMatches.size(); index++) {
            FullTextSearchResult match = safeMatches.get(index);
            hits.add(new RankedHit(index + 1, match.sourceType(), match.sourceId(), match.chunkId(), match.score(),
                    List.of(RetrievalChannel.FULL_TEXT)));
        }
        return List.copyOf(hits);
    }

    private List<RankedHit> retrievalHits(List<RetrievalResult> matches) {
        List<RankedHit> hits = new ArrayList<>();
        List<RetrievalResult> safeMatches = safeList(matches);
        for (int index = 0; index < safeMatches.size(); index++) {
            RetrievalResult match = safeMatches.get(index);
            hits.add(new RankedHit(index + 1, match.sourceType(), match.sourceId(), match.chunkId(), match.score(),
                    safeList(match.channels())));
        }
        return List.copyOf(hits);
    }

    private List<RankedHit> rerankedHits(RerankOutcome outcome) {
        List<RankedHit> hits = new ArrayList<>();
        List<RetrievalResult> safeMatches = safeList(outcome.results());
        List<Double> scores = safeList(outcome.scores());
        for (int index = 0; index < safeMatches.size(); index++) {
            RetrievalResult match = safeMatches.get(index);
            hits.add(new RankedHit(index + 1, match.sourceType(), match.sourceId(), match.chunkId(),
                    scores.get(index), safeList(match.channels())));
        }
        return List.copyOf(hits);
    }

    private String metadataValue(Metadata metadata, String key) {
        if (metadata == null) {
            return "";
        }
        String value = metadata.getString(key);
        return value == null ? "" : value;
    }

    private List<StageSummary> summarize(List<CaseResult> cases) {
        Map<String, List<StageResult>> byStage = new LinkedHashMap<>();
        STAGE_ORDER.forEach(stage -> byStage.put(stage, new ArrayList<>()));
        for (CaseResult caseResult : cases) {
            for (StageResult stage : caseResult.stages()) {
                byStage.computeIfAbsent(stage.stage(), ignored -> new ArrayList<>()).add(stage);
            }
        }
        return byStage.entrySet().stream()
                .map(entry -> summarize(entry.getKey(), entry.getValue()))
                .toList();
    }

    private StageSummary summarize(String stage, List<StageResult> results) {
        return new StageSummary(
                stage,
                results.size(),
                average(results, MetricSelector.RECALL_AT_5),
                average(results, MetricSelector.RECALL_AT_10),
                average(results, MetricSelector.RECALL_AT_20),
                average(results, MetricSelector.MRR_AT_10),
                average(results, MetricSelector.NDCG_AT_10),
                average(results, MetricSelector.HIT_AT_6),
                rankingMetrics.p95(results.stream().map(StageResult::latencyMs).toList()));
    }

    private double average(List<StageResult> results, MetricSelector selector) {
        if (results.isEmpty()) {
            return 0D;
        }
        double average = results.stream()
                .map(StageResult::metrics)
                .mapToDouble(selector::value)
                .average()
                .orElse(0D);
        return Math.round(average * 1_000_000D) / 1_000_000D;
    }

    private DatasetSnapshot datasetSnapshot(LoadedDataset loaded) {
        RagBenchmarkDataset dataset = loaded.dataset();
        return new DatasetSnapshot(dataset.datasetId(), dataset.corpusVersion(), dataset.cases().size(),
                loaded.sha256(), loaded.path());
    }

    private ConfigurationSnapshot configurationSnapshot() {
        EmbeddingProfile profile = embeddingGateway.getProfile();
        return new ConfigurationSnapshot(
                profile.provider(),
                profile.modelId(),
                profile.indexVersion(),
                profile.dimension(),
                profile.normalize(),
                profile.metric(),
                "milvus",
                ragProperties.getFullTextProvider(),
                ragReranker.configuredProviderName(),
                ragReranker.configuredModelName(),
                ragProperties.getCandidateTopK(),
                ragProperties.getTopK(),
                ragProperties.getMinScore(),
                ragProperties.getMaxContextChars());
    }

    private QualityGate qualityGate(List<StageSummary> summaries) {
        StageSummary recallStage = summaries.stream()
                .filter(summary -> STAGE_RRF.equals(summary.stage()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("RAG评测缺少RRF汇总"));
        StageSummary rerankStage = summaries.stream()
                .filter(summary -> STAGE_RERANK.equals(summary.stage()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("RAG评测缺少精排汇总"));
        boolean passed = recallStage.recallAt20() >= RECALL_AT_20_TARGET
                && rerankStage.mrrAt10() >= MRR_AT_10_TARGET;
        return new QualityGate(recallStage.stage(), rerankStage.stage(), RECALL_AT_20_TARGET, MRR_AT_10_TARGET,
                recallStage.recallAt20(), rerankStage.mrrAt10(), passed);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private enum MetricSelector {
        RECALL_AT_5 {
            @Override
            double value(RankingMetrics metrics) {
                return metrics.recallAt5();
            }
        },
        RECALL_AT_10 {
            @Override
            double value(RankingMetrics metrics) {
                return metrics.recallAt10();
            }
        },
        RECALL_AT_20 {
            @Override
            double value(RankingMetrics metrics) {
                return metrics.recallAt20();
            }
        },
        MRR_AT_10 {
            @Override
            double value(RankingMetrics metrics) {
                return metrics.mrrAt10();
            }
        },
        NDCG_AT_10 {
            @Override
            double value(RankingMetrics metrics) {
                return metrics.ndcgAt10();
            }
        },
        HIT_AT_6 {
            @Override
            double value(RankingMetrics metrics) {
                return metrics.hitAt6();
            }
        };

        abstract double value(RankingMetrics metrics);
    }
}
