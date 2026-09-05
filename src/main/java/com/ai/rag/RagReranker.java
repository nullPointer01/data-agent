package com.ai.rag;

import com.ai.rag.rerank.HeuristicRerankProvider;
import com.ai.rag.rerank.HttpCrossEncoderRerankProvider;
import com.ai.rag.rerank.RerankProvider.RerankCandidate;
import com.ai.rag.rerank.RerankProvider.RerankProviderException;
import com.ai.rag.rerank.RerankProvider.RerankRequest;
import com.ai.rag.rerank.RerankProvider.RerankResponse;
import com.ai.rag.rerank.RerankProvider.RerankScore;
import com.ai.rag.retrieval.RetrievalResult;
import com.ai.vector.VectorMetadataKeys;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 协调 Cross-Encoder 精排、响应校验和确定性规则降级。
 *
 * @author data-agent
 */
@Component
public class RagReranker {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagReranker.class);

    private final RerankerProperties properties;
    private final HttpCrossEncoderRerankProvider crossEncoderProvider;
    private final HeuristicRerankProvider heuristicProvider;

    public RagReranker(RerankerProperties properties,
            HttpCrossEncoderRerankProvider crossEncoderProvider,
            HeuristicRerankProvider heuristicProvider) {
        this.properties = properties;
        this.crossEncoderProvider = crossEncoderProvider;
        this.heuristicProvider = heuristicProvider;
    }

    /**
     * 对旧向量候选执行确定性规则重排。
     *
     * @param matches 向量候选
     * @param analysis 查询分析
     * @return 重排后的候选
     */
    public List<EmbeddingMatch<TextSegment>> rerank(List<EmbeddingMatch<TextSegment>> matches,
            RagQueryAnalysis analysis) {
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }
        List<RerankCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < matches.size(); index++) {
            EmbeddingMatch<TextSegment> match = matches.get(index);
            TextSegment segment = match.embedded();
            String content = segment == null ? "" : segment.text();
            String sourceType = segment == null || segment.metadata() == null
                    ? ""
                    : segment.metadata().getString(VectorMetadataKeys.TYPE);
            candidates.add(new RerankCandidate(index, content, match.score(), sourceType));
        }
        RerankResponse response = heuristicProvider.rerank(new RerankRequest(
                queryOf(analysis), keywordsOf(analysis), candidates));
        return response.scores().stream().map(score -> matches.get(score.index())).toList();
    }

    /**
     * 对混合检索候选执行当前配置的精排策略。
     *
     * @param results RRF 融合候选
     * @param analysis 查询分析
     * @return 重排后的原始候选，向量、BM25 与通道信息保持不变
     */
    public List<RetrievalResult> rerankHybrid(List<RetrievalResult> results, RagQueryAnalysis analysis) {
        return rerankHybridWithTrace(results, analysis).results();
    }

    /**
     * 对混合检索候选精排，并返回本次请求独立的 Trace 证据。
     *
     * @param results RRF 融合候选
     * @param analysis 查询分析
     * @return 排序结果与 Provider 执行证据
     */
    public RerankOutcome rerankHybridWithTrace(List<RetrievalResult> results, RagQueryAnalysis analysis) {
        if (results == null || results.isEmpty()) {
            return new RerankOutcome(List.of(), List.of(), RerankEvidence.empty());
        }
        List<RetrievalResult> safeResults = results.stream().filter(result -> result != null).toList();
        if (safeResults.isEmpty()) {
            return new RerankOutcome(List.of(), List.of(), RerankEvidence.empty());
        }
        if (!properties.isEnabled()) {
            return heuristicOutcome(safeResults, analysis, safeResults.size(), false, "", 0L,
                    heuristicProvider.providerName(), heuristicProvider.modelName());
        }

        int externalCandidateCount = Math.min(safeResults.size(), properties.getMaxCandidates());
        List<RerankCandidate> candidates = candidates(safeResults, externalCandidateCount);
        RerankRequest request = new RerankRequest(queryOf(analysis), keywordsOf(analysis), candidates);
        long startedNanos = System.nanoTime();
        try {
            RerankResponse response = crossEncoderProvider.rerank(request);
            MappedRerankResults mapped = mapScores(safeResults, externalCandidateCount, response);
            long durationMs = elapsedMillis(startedNanos);
            return new RerankOutcome(mapped.results(), mapped.scores(), new RerankEvidence(
                    crossEncoderProvider.providerName(), crossEncoderProvider.modelName(), "", durationMs,
                    externalCandidateCount, response.scores().size(), false, ""));
        } catch (RuntimeException exception) {
            long durationMs = elapsedMillis(startedNanos);
            String category = errorCategory(exception);
            if (!properties.isFailOpen()) {
                throw exception;
            }
            LOGGER.warn("Cross-Encoder 精排失败，使用规则降级: provider={}, model={}, category={}, exception={}",
                    crossEncoderProvider.providerName(), crossEncoderProvider.modelName(), category,
                    exception.getClass().getSimpleName());
            return heuristicOutcome(safeResults, analysis, externalCandidateCount, true, category, durationMs,
                    crossEncoderProvider.providerName(), crossEncoderProvider.modelName());
        }
    }

    /**
     * 返回当前规则重排使用的可解释启发式分数。
     *
     * @param result 混合检索候选
     * @param analysis 查询分析
     * @return RRF 分数、关键词覆盖和来源权重的组合分数
     */
    public double scoreHybrid(RetrievalResult result, RagQueryAnalysis analysis) {
        if (result == null) {
            return 0D;
        }
        return heuristicProvider.score(candidate(0, result), keywordsOf(analysis));
    }

    /**
     * 返回外部模型精排是否启用。
     *
     * @return 是否启用
     */
    public boolean isCrossEncoderEnabled() {
        return properties.isEnabled();
    }

    /**
     * 返回配置的外部 Provider 名称。
     *
     * @return Provider 名称
     */
    public String configuredProviderName() {
        return properties.isEnabled() ? crossEncoderProvider.providerName() : heuristicProvider.providerName();
    }

    /**
     * 返回配置的模型名称。
     *
     * @return 模型名称或规则版本
     */
    public String configuredModelName() {
        return properties.isEnabled() ? crossEncoderProvider.modelName() : heuristicProvider.modelName();
    }

    private RerankOutcome heuristicOutcome(List<RetrievalResult> results, RagQueryAnalysis analysis,
            int inputCount, boolean fallback, String errorCategory, long externalDurationMs,
            String attemptedProvider, String attemptedModel) {
        long startedNanos = System.nanoTime();
        RerankResponse response = heuristicProvider.rerank(new RerankRequest(
                queryOf(analysis), keywordsOf(analysis), candidates(results, results.size())));
        List<RetrievalResult> ranked = response.scores().stream()
                .map(score -> results.get(score.index()))
                .toList();
        List<Double> scores = response.scores().stream().map(RerankScore::score).toList();
        long durationMs = externalDurationMs + elapsedMillis(startedNanos);
        String fallbackProvider = fallback ? heuristicProvider.providerName() : "";
        return new RerankOutcome(ranked, scores,
                new RerankEvidence(attemptedProvider, attemptedModel, fallbackProvider,
                durationMs, inputCount, response.scores().size(), fallback, errorCategory));
    }

    private MappedRerankResults mapScores(List<RetrievalResult> results, int candidateCount,
            RerankResponse response) {
        if (response == null || response.scores() == null) {
            throw contractError("Reranker 响应为空");
        }
        Map<Integer, Double> scoresByIndex = new HashMap<>();
        Set<Integer> seenIndexes = new HashSet<>();
        for (RerankScore score : response.scores()) {
            if (score == null || score.index() < 0 || score.index() >= candidateCount) {
                throw contractError("Reranker 返回越界候选索引");
            }
            if (!Double.isFinite(score.score())) {
                throw contractError("Reranker 返回非有限相关性分数");
            }
            if (!seenIndexes.add(score.index())) {
                throw contractError("Reranker 返回重复候选索引");
            }
            scoresByIndex.put(score.index(), score.score());
        }
        if (scoresByIndex.size() != candidateCount) {
            throw contractError("Reranker 返回候选数量不完整");
        }

        List<Integer> orderedIndexes = scoresByIndex.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparingInt(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();
        List<RetrievalResult> ranked = new ArrayList<>(results.size());
        List<Double> rankedScores = new ArrayList<>(results.size());
        orderedIndexes.forEach(index -> {
            ranked.add(results.get(index));
            rankedScores.add(scoresByIndex.get(index));
        });
        if (candidateCount < results.size()) {
            ranked.addAll(results.subList(candidateCount, results.size()));
            results.subList(candidateCount, results.size()).stream()
                    .map(RetrievalResult::score)
                    .forEach(rankedScores::add);
        }
        return new MappedRerankResults(List.copyOf(ranked), List.copyOf(rankedScores));
    }

    private List<RerankCandidate> candidates(List<RetrievalResult> results, int limit) {
        List<RerankCandidate> candidates = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            candidates.add(candidate(index, results.get(index)));
        }
        return List.copyOf(candidates);
    }

    private RerankCandidate candidate(int index, RetrievalResult result) {
        return new RerankCandidate(index, result.content(), result.score(), result.sourceType());
    }

    private RerankProviderException contractError(String message) {
        return new RerankProviderException("RESPONSE_CONTRACT", message, false);
    }

    private String errorCategory(RuntimeException exception) {
        if (exception instanceof RerankProviderException providerException) {
            return providerException.category();
        }
        return "UNKNOWN";
    }

    private String queryOf(RagQueryAnalysis analysis) {
        return analysis == null || analysis.rewrittenQuery() == null ? "" : analysis.rewrittenQuery();
    }

    private List<String> keywordsOf(RagQueryAnalysis analysis) {
        return analysis == null || analysis.keywords() == null ? List.of() : analysis.keywords();
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    /**
     * 一次精排的有序候选和执行证据。
     *
     * @param results 有序原始候选
     * @param scores 与有序候选逐项对齐的精排分数
     * @param evidence Provider 执行证据
     */
    public record RerankOutcome(List<RetrievalResult> results, List<Double> scores, RerankEvidence evidence) {

        public RerankOutcome {
            results = results == null ? List.of() : List.copyOf(results);
            scores = scores == null ? List.of() : List.copyOf(scores);
            evidence = evidence == null ? RerankEvidence.empty() : evidence;
            if (results.size() != scores.size()) {
                throw new IllegalArgumentException("精排候选与分数数量不一致");
            }
        }
    }

    /**
     * 可安全进入 RAG Trace 的低基数精排证据，不包含查询或 Chunk 正文。
     *
     * @param provider 尝试执行的 Provider
     * @param model 模型名称或规则版本
     * @param fallbackProvider 实际降级 Provider，未降级时为空
     * @param durationMs 精排总耗时
     * @param inputCount 输入候选数量
     * @param outputCount 输出分数数量
     * @param fallback 是否发生降级
     * @param errorCategory 低基数错误类别
     */
    public record RerankEvidence(String provider, String model, String fallbackProvider,
            long durationMs, int inputCount, int outputCount, boolean fallback, String errorCategory) {

        /**
         * 返回没有候选时的空证据。
         *
         * @return 空证据
         */
        public static RerankEvidence empty() {
            return new RerankEvidence("", "", "", 0L, 0, 0, false, "");
        }
    }

    private record MappedRerankResults(List<RetrievalResult> results, List<Double> scores) {
    }
}
