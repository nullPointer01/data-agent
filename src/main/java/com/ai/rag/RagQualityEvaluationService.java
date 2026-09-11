package com.ai.rag;

import com.ai.rag.dto.RagCitation;
import com.ai.rag.dto.RagContextResponse;
import com.ai.rag.dto.RagQualityEvaluationRequest;
import com.ai.rag.dto.RagQualityEvaluationResponse;
import com.ai.rag.dto.RagQualityMetric;
import com.ai.rag.dto.RagRetrievalTrace;
import com.ai.rag.eval.RagRelevanceEvaluator;
import com.ai.rag.eval.RagRelevanceEvaluator.RelevanceEvaluation;
import com.ai.rag.retrieval.RetrievalChannel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * RAG 质量评估服务。
 *
 * <p>当前评估使用确定性规则，便于离线测试和前端验收；后续可在此基础上接入人工标注集或 LLM Judge。</p>
 *
 * @author data-agent
 */
@Service
public class RagQualityEvaluationService {

    private static final String METRIC_RELEVANCE = "RELEVANCE";
    private static final String METRIC_CITATION_ACCURACY = "CITATION_ACCURACY";
    private static final String METRIC_HYBRID_COVERAGE = "HYBRID_COVERAGE";
    private static final String METRIC_LATENCY = "LATENCY";
    private static final String METRIC_SOURCE_COVERAGE = "SOURCE_COVERAGE";
    private static final double RELEVANCE_WEIGHT = 0.35D;
    private static final double CITATION_WEIGHT = 0.35D;
    private static final double HYBRID_WEIGHT = 0.15D;
    private static final double LATENCY_WEIGHT = 0.15D;
    private static final double SOURCE_WEIGHT_WITH_EXPECTED_SOURCE = 0.20D;
    private static final double RELEVANCE_WEIGHT_WITH_EXPECTED_SOURCE = 0.30D;
    private static final double CITATION_WEIGHT_WITH_EXPECTED_SOURCE = 0.30D;
    private static final double HYBRID_WEIGHT_WITH_EXPECTED_SOURCE = 0.10D;
    private static final double LATENCY_WEIGHT_WITH_EXPECTED_SOURCE = 0.10D;
    private static final double LATENCY_EMPTY_SCORE = 0D;
    private static final double LATENCY_PASS_SCORE = 1D;
    private static final int HUNDRED_PERCENT = 100;

    private final RagProperties ragProperties;
    private final RagRelevanceEvaluator relevanceEvaluator;

    public RagQualityEvaluationService(RagProperties ragProperties, RagRelevanceEvaluator relevanceEvaluator) {
        this.ragProperties = ragProperties;
        this.relevanceEvaluator = relevanceEvaluator;
    }

    /**
     * 评估一次 RAG 检索结果的相关性、引用、混合召回和延迟。
     *
     * @param request 质量评估请求
     * @return 质量评估结果
     */
    public RagQualityEvaluationResponse evaluate(RagQualityEvaluationRequest request) {
        RagQualityEvaluationRequest safeRequest = request == null
                ? new RagQualityEvaluationRequest("", RagContextResponse.empty(), List.of(), List.of())
                : request;
        RagContextResponse response = safeRequest.response() == null ? RagContextResponse.empty() : safeRequest.response();
        List<RagCitation> citations = safeList(response.getCitations());
        RelevanceEvaluation relevance = relevanceEvaluator.evaluate(safeRequest.expectedKeywords(), response);
        double relevanceScore = relevance.score();
        CitationScore citationScore = citationScore(response, citations, safeList(safeRequest.expectedSourceIds()));
        double hybridCoverage = hybridCoverage(response.getTrace(), citations);
        double latencyScore = latencyScore(response.getTrace());
        double sourceCoverage = citationScore.sourceCoverage();
        List<RagQualityMetric> metrics = metrics(relevance, citationScore, hybridCoverage, latencyScore);
        double overallScore = overallScore(relevanceScore, citationScore.accuracy(), hybridCoverage,
                latencyScore, sourceCoverage, citationScore.hasExpectedSource());
        List<String> issues = issues(metrics);
        List<String> recommendations = recommendations(metrics, citationScore.hasExpectedSource());
        boolean passed = overallScore >= ragProperties.getQuality().getOverallPassThreshold()
                && metrics.stream().allMatch(RagQualityMetric::passed);
        return new RagQualityEvaluationResponse(passed, round(overallScore), round(relevanceScore),
                round(citationScore.accuracy()), round(hybridCoverage), round(latencyScore), round(sourceCoverage),
                relevance.expectedKeywords().size(), relevance.matchedKeywords().size(), citations.size(),
                citationScore.validCitationCount(), relevance.matchedKeywords(), metrics, issues, recommendations);
    }

    private List<RagQualityMetric> metrics(RelevanceEvaluation relevance, CitationScore citationScore,
            double hybridCoverage, double latencyScore) {
        List<RagQualityMetric> metrics = new ArrayList<>();
        metrics.add(metric(METRIC_RELEVANCE, "检索相关性", relevance.score(),
                ragProperties.getQuality().getRelevanceThreshold(),
                relevance.detail()));
        metrics.add(metric(METRIC_CITATION_ACCURACY, "引用准确率", citationScore.accuracy(),
                ragProperties.getQuality().getCitationAccuracyThreshold(), citationScore.detail()));
        metrics.add(metric(METRIC_HYBRID_COVERAGE, "混合召回覆盖", hybridCoverage,
                ragProperties.getQuality().getHybridCoverageThreshold(),
                "向量和全文通道综合覆盖 " + percent(hybridCoverage)));
        metrics.add(metric(METRIC_LATENCY, "端到端延迟", latencyScore,
                ragProperties.getQuality().getLatencyScoreThreshold(),
                "延迟健康度 " + percent(latencyScore)));
        if (citationScore.hasExpectedSource()) {
            metrics.add(metric(METRIC_SOURCE_COVERAGE, "期望来源覆盖", citationScore.sourceCoverage(),
                    ragProperties.getQuality().getSourceCoverageThreshold(),
                    "命中期望来源 " + percent(citationScore.sourceCoverage())));
        }
        return metrics;
    }

    private RagQualityMetric metric(String key, String name, double score, double threshold, String detail) {
        double roundedScore = round(score);
        return new RagQualityMetric(key, name, roundedScore, threshold, score >= threshold, detail);
    }

    private CitationScore citationScore(RagContextResponse response, List<RagCitation> citations,
            List<String> expectedSourceIds) {
        int validCitationCount = (int) citations.stream()
                .filter(citation -> isValidCitation(response, citation))
                .count();
        double citationCompleteness = ratio(validCitationCount, citations.size());
        List<String> normalizedSourceIds = normalizeTerms(expectedSourceIds);
        if (normalizedSourceIds.isEmpty()) {
            return new CitationScore(citationCompleteness, 0D, false, validCitationCount,
                    "未提供标注来源，按引用元数据完整性评估 " + percent(citationCompleteness));
        }
        int matchedSourceCount = matchedSourceCount(normalizedSourceIds, citations);
        double sourceCoverage = ratio(matchedSourceCount, normalizedSourceIds.size());
        double accuracy = (citationCompleteness + sourceCoverage) / 2D;
        return new CitationScore(accuracy, sourceCoverage, true, validCitationCount,
                "引用完整性和期望来源覆盖综合得分 " + percent(accuracy));
    }

    private boolean isValidCitation(RagContextResponse response, RagCitation citation) {
        if (citation == null) {
            return false;
        }
        boolean hasMetadata = StringUtils.hasText(citation.referenceId())
                && StringUtils.hasText(citation.sourceId())
                && StringUtils.hasText(citation.chunkId())
                && StringUtils.hasText(citation.snippet());
        if (!hasMetadata) {
            return false;
        }
        if (!StringUtils.hasText(response.getContext())) {
            return true;
        }
        return response.getContext().contains("[" + citation.referenceId() + "]")
                || response.getContext().contains(citation.referenceId());
    }

    private int matchedSourceCount(List<String> expectedSourceIds, List<RagCitation> citations) {
        Set<String> actualSourceIds = new LinkedHashSet<>();
        for (RagCitation citation : citations) {
            actualSourceIds.add(normalizeForMatch(citation.sourceId()));
            actualSourceIds.add(normalizeForMatch(citation.chunkId()));
        }
        return (int) expectedSourceIds.stream()
                .filter(actualSourceIds::contains)
                .count();
    }

    private double hybridCoverage(RagRetrievalTrace trace, List<RagCitation> citations) {
        RagRetrievalTrace safeTrace = trace == null ? RagRetrievalTrace.empty() : trace;
        double channelScore = 0D;
        if (safeTrace.vectorCandidateCount() > 0 || hasChannel(citations, RetrievalChannel.VECTOR)) {
            channelScore += 0.5D;
        }
        if (safeTrace.fullTextCandidateCount() > 0 || hasChannel(citations, RetrievalChannel.FULL_TEXT)) {
            channelScore += 0.5D;
        }
        int denominator = safeTrace.finalCount() > 0 ? safeTrace.finalCount() : citations.size();
        double hybridChunkScore = ratio(Math.max(safeTrace.hybridCandidateCount(), hybridCitationCount(citations)),
                denominator);
        return Math.min(1D, channelScore * 0.6D + hybridChunkScore * 0.4D);
    }

    private boolean hasChannel(List<RagCitation> citations, String channel) {
        return citations.stream()
                .anyMatch(citation -> citation.channels() != null && citation.channels().contains(channel));
    }

    private int hybridCitationCount(List<RagCitation> citations) {
        return (int) citations.stream()
                .filter(citation -> citation.channels() != null
                        && citation.channels().contains(RetrievalChannel.VECTOR)
                        && citation.channels().contains(RetrievalChannel.FULL_TEXT))
                .count();
    }

    private double latencyScore(RagRetrievalTrace trace) {
        RagRetrievalTrace safeTrace = trace == null ? RagRetrievalTrace.empty() : trace;
        if (!safeTrace.enabled() || safeTrace.totalTimeMs() <= 0) {
            return LATENCY_EMPTY_SCORE;
        }
        long thresholdMs = ragProperties.getHealth().getRagLatencyThresholdMs();
        if (safeTrace.totalTimeMs() <= thresholdMs) {
            return LATENCY_PASS_SCORE;
        }
        return Math.max(0D, (double) thresholdMs / safeTrace.totalTimeMs());
    }

    private double overallScore(double relevanceScore, double citationAccuracy, double hybridCoverage,
            double latencyScore, double sourceCoverage, boolean hasExpectedSource) {
        if (hasExpectedSource) {
            return relevanceScore * RELEVANCE_WEIGHT_WITH_EXPECTED_SOURCE
                    + citationAccuracy * CITATION_WEIGHT_WITH_EXPECTED_SOURCE
                    + hybridCoverage * HYBRID_WEIGHT_WITH_EXPECTED_SOURCE
                    + latencyScore * LATENCY_WEIGHT_WITH_EXPECTED_SOURCE
                    + sourceCoverage * SOURCE_WEIGHT_WITH_EXPECTED_SOURCE;
        }
        return relevanceScore * RELEVANCE_WEIGHT
                + citationAccuracy * CITATION_WEIGHT
                + hybridCoverage * HYBRID_WEIGHT
                + latencyScore * LATENCY_WEIGHT;
    }

    private List<String> issues(List<RagQualityMetric> metrics) {
        return metrics.stream()
                .filter(metric -> !metric.passed())
                .map(metric -> metric.name() + "未达标：" + metric.detail())
                .toList();
    }

    private List<String> recommendations(List<RagQualityMetric> metrics, boolean hasExpectedSource) {
        List<String> recommendations = new ArrayList<>();
        for (RagQualityMetric metric : metrics) {
            if (metric.passed()) {
                continue;
            }
            recommendations.add(recommendation(metric.key(), hasExpectedSource));
        }
        return recommendations.stream().distinct().toList();
    }

    private String recommendation(String key, boolean hasExpectedSource) {
        return switch (key) {
            case METRIC_RELEVANCE -> "补充更贴近业务问题的标题、段落和关键词，必要时调低最小召回分数。";
            case METRIC_CITATION_ACCURACY -> hasExpectedSource
                    ? "检查引用是否命中标注来源，并确认分块元数据中的来源编号、片段编号和摘要完整。"
                    : "补齐引用的来源编号、片段编号和上下文引用标记，避免只返回无溯源文本。";
            case METRIC_HYBRID_COVERAGE -> "确认向量索引和全文索引同步写入，避免单通道召回造成结果偏窄。";
            case METRIC_LATENCY -> "观察 trace 中的召回、重排和压缩耗时，优先优化最高耗时阶段。";
            case METRIC_SOURCE_COVERAGE -> "用标注样本核对期望来源，必要时调整分块粒度和重排权重。";
            default -> "根据质量指标继续补充样本并回归验证。";
        };
    }

    private List<String> normalizeTerms(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String term : terms) {
            String value = normalizeForMatch(term);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private double ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return Math.min(1D, (double) numerator / denominator);
    }

    private double round(double value) {
        return Math.round(value * HUNDRED_PERCENT) / (double) HUNDRED_PERCENT;
    }

    private String percent(double value) {
        return Math.round(value * HUNDRED_PERCENT) + "%";
    }

    private String normalizeForMatch(String value) {
        return blankToEmpty(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[，。！？；：,.!?;:()（）\\[\\]【】\"'`]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 引用评估的中间结果。
     *
     * @param accuracy 引用准确率或完整性得分
     * @param sourceCoverage 期望来源覆盖率
     * @param hasExpectedSource 是否提供了期望来源
     * @param validCitationCount 有效引用数量
     * @param detail 评估说明
     */
    private record CitationScore(double accuracy,
            double sourceCoverage,
            boolean hasExpectedSource,
            int validCitationCount,
            String detail) {
    }
}
