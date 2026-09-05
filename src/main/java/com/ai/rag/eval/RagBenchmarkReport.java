package com.ai.rag.eval;

import java.time.Instant;
import java.util.List;

/**
 * 一次可复现的 RAG 分阶段检索评测报告。
 *
 * @param runId 本次运行编号
 * @param startedAt 开始时间
 * @param completedAt 完成时间
 * @param dataset 数据集快照
 * @param configuration 检索配置快照
 * @param stageSummaries 各阶段汇总指标
 * @param caseResults 逐案排名和指标
 * @param failedCaseIds 未进入最终TopK的用例编号
 * @param qualityGate 当前精排结果是否达到预设目标
 * @author data-agent
 */
public record RagBenchmarkReport(
        String runId,
        Instant startedAt,
        Instant completedAt,
        DatasetSnapshot dataset,
        ConfigurationSnapshot configuration,
        List<StageSummary> stageSummaries,
        List<CaseResult> caseResults,
        List<String> failedCaseIds,
        QualityGate qualityGate) {

    /**
     * 黄金集版本快照。
     */
    public record DatasetSnapshot(
            String datasetId,
            String corpusVersion,
            int caseCount,
            String sha256,
            String path) {
    }

    /**
     * 本次运行使用的模型和检索参数。
     */
    public record ConfigurationSnapshot(
            String embeddingProvider,
            String embeddingModelId,
            String embeddingIndexVersion,
            int embeddingDimension,
            boolean embeddingNormalized,
            String embeddingMetric,
            String vectorProvider,
            String fullTextProvider,
            String rerankerProvider,
            String rerankerModel,
            int candidateTopK,
            int topK,
            double minScore,
            int maxContextChars) {
    }

    /**
     * 一个检索阶段在整个黄金集上的平均指标。
     */
    public record StageSummary(
            String stage,
            int caseCount,
            double recallAt5,
            double recallAt10,
            double recallAt20,
            double mrrAt10,
            double ndcgAt10,
            double hitAt6,
            long p95LatencyMs) {
    }

    /**
     * 单条用例的各阶段结果。
     */
    public record CaseResult(
            String caseId,
            String query,
            List<String> tags,
            String failureStage,
            String errorType,
            String rerankerProvider,
            String rerankerModel,
            String rerankerFallbackProvider,
            boolean rerankerFallback,
            String rerankerErrorCategory,
            List<StageResult> stages) {
    }

    /**
     * 单条用例在一个阶段的排名结果。
     */
    public record StageResult(
            String stage,
            RankingMetrics metrics,
            long latencyMs,
            List<RankedHit> hits) {
    }

    /**
     * 单条用例的确定性排序指标。
     */
    public record RankingMetrics(
            double recallAt5,
            double recallAt10,
            double recallAt20,
            double mrrAt10,
            double ndcgAt10,
            double hitAt6,
            int firstRelevantRank,
            int relevantCount) {
    }

    /**
     * 去除正文后的候选排名证据。
     */
    public record RankedHit(
            int rank,
            String sourceType,
            String sourceId,
            String chunkId,
            double score,
            List<String> channels) {
    }

    /**
     * 当前提案定义的召回质量门禁。
     */
    public record QualityGate(
            String recallStage,
            String rerankStage,
            double recallAt20Target,
            double mrrAt10Target,
            double actualRecallAt20,
            double actualMrrAt10,
            boolean passed) {
    }
}
