package com.ai.rag.eval;

import com.ai.rag.eval.RagBenchmarkReport.RankedHit;
import com.ai.rag.eval.RagBenchmarkReport.RankingMetrics;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 计算 Recall、MRR、NDCG、Hit 和 P95 等确定性检索指标。
 *
 * @author data-agent
 */
@Component
public class RagRankingMetrics {

    private static final int NOT_FOUND_RANK = 0;

    /**
     * 计算一条用例在当前有序候选上的指标。
     *
     * @param benchmarkCase 人工标注用例
     * @param hits 当前阶段候选
     * @return 排序指标
     */
    public RankingMetrics evaluate(RagBenchmarkCase benchmarkCase, List<RankedHit> hits) {
        Relevance relevance = relevanceOf(benchmarkCase);
        List<RankedHit> safeHits = hits == null ? List.of() : hits;
        int firstRelevantRank = firstRelevantRank(safeHits, relevance);
        return new RankingMetrics(
                round(recallAt(safeHits, relevance, 5)),
                round(recallAt(safeHits, relevance, 10)),
                round(recallAt(safeHits, relevance, 20)),
                round(reciprocalRankAt(firstRelevantRank, 10)),
                round(ndcgAt(safeHits, relevance, 10)),
                firstRelevantRank > 0 && firstRelevantRank <= 6 ? 1D : 0D,
                firstRelevantRank,
                relevance.grades().size());
    }

    /**
     * 计算离散延迟样本的最近秩P95。
     *
     * @param latencies 延迟毫秒列表
     * @return P95毫秒值
     */
    public long p95(List<Long> latencies) {
        if (latencies == null || latencies.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = latencies.stream()
                .filter(value -> value != null && value >= 0L)
                .sorted()
                .toList();
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95D) - 1);
        return sorted.get(index);
    }

    private Relevance relevanceOf(RagBenchmarkCase benchmarkCase) {
        Map<String, Integer> gradedChunks = positiveGrades(benchmarkCase.relevanceGrades());
        Set<String> expectedChunks = normalizedSet(benchmarkCase.expectedChunkIds());
        boolean chunkLevel = !expectedChunks.isEmpty() || !gradedChunks.isEmpty();
        Map<String, Integer> grades = new LinkedHashMap<>();
        if (chunkLevel) {
            expectedChunks.forEach(chunkId -> grades.put(chunkId, 1));
            gradedChunks.forEach((chunkId, grade) -> grades.merge(chunkId, grade, Math::max));
        } else {
            normalizedSet(benchmarkCase.expectedSourceIds()).forEach(sourceId -> grades.put(sourceId, 1));
        }
        return new Relevance(chunkLevel, grades);
    }

    private Map<String, Integer> positiveGrades(Map<String, Integer> values) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (values == null) {
            return result;
        }
        values.forEach((key, value) -> {
            if (StringUtils.hasText(key) && value != null && value > 0) {
                result.put(key.trim(), value);
            }
        });
        return result;
    }

    private Set<String> normalizedSet(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private double recallAt(List<RankedHit> hits, Relevance relevance, int k) {
        if (relevance.grades().isEmpty()) {
            return 0D;
        }
        Set<String> found = new HashSet<>();
        for (int index = 0; index < Math.min(k, hits.size()); index++) {
            String key = relevance.keyOf(hits.get(index));
            if (relevance.grades().containsKey(key)) {
                found.add(key);
            }
        }
        return (double) found.size() / relevance.grades().size();
    }

    private int firstRelevantRank(List<RankedHit> hits, Relevance relevance) {
        for (int index = 0; index < hits.size(); index++) {
            if (relevance.grades().containsKey(relevance.keyOf(hits.get(index)))) {
                return index + 1;
            }
        }
        return NOT_FOUND_RANK;
    }

    private double reciprocalRankAt(int firstRelevantRank, int k) {
        if (firstRelevantRank <= 0 || firstRelevantRank > k) {
            return 0D;
        }
        return 1D / firstRelevantRank;
    }

    private double ndcgAt(List<RankedHit> hits, Relevance relevance, int k) {
        double dcg = 0D;
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < Math.min(k, hits.size()); index++) {
            String key = relevance.keyOf(hits.get(index));
            int grade = seen.add(key) ? relevance.grades().getOrDefault(key, 0) : 0;
            dcg += discountedGain(grade, index + 1);
        }
        List<Integer> idealGrades = new ArrayList<>(relevance.grades().values());
        idealGrades.sort(Comparator.reverseOrder());
        double idealDcg = 0D;
        for (int index = 0; index < Math.min(k, idealGrades.size()); index++) {
            idealDcg += discountedGain(idealGrades.get(index), index + 1);
        }
        return idealDcg == 0D ? 0D : dcg / idealDcg;
    }

    private double discountedGain(int grade, int rank) {
        if (grade <= 0) {
            return 0D;
        }
        return (Math.pow(2D, grade) - 1D) / (Math.log(rank + 1D) / Math.log(2D));
    }

    private double round(double value) {
        return Math.round(value * 1_000_000D) / 1_000_000D;
    }

    private record Relevance(boolean chunkLevel, Map<String, Integer> grades) {

        private String keyOf(RankedHit hit) {
            return chunkLevel ? blankToEmpty(hit.chunkId()) : blankToEmpty(hit.sourceId());
        }

        private String blankToEmpty(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
