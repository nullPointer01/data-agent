package com.ai.rag.rerank;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

/**
 * 使用 RRF 分数、关键词覆盖率和来源权重的确定性规则精排 Provider。
 *
 * @author data-agent
 */
@Component
public class HeuristicRerankProvider implements RerankProvider {

    private static final double RETRIEVAL_WEIGHT = 0.72D;
    private static final double KEYWORD_WEIGHT = 0.2D;
    private static final double SOURCE_WEIGHT = 0.08D;
    private static final double KNOWLEDGE_SOURCE_BOOST = 0.05D;
    private static final String PROVIDER_NAME = "heuristic";
    private static final String MODEL_NAME = "rrf-keyword-source-v1";

    @Override
    public RerankResponse rerank(RerankRequest request) {
        List<String> keywords = request.keywords() == null ? List.of() : request.keywords();
        List<RerankScore> scores = request.candidates().stream()
                .map(candidate -> new RerankScore(candidate.index(), score(candidate, keywords)))
                .sorted(Comparator.comparingDouble(RerankScore::score).reversed()
                        .thenComparingInt(RerankScore::index))
                .toList();
        return new RerankResponse(scores);
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    /**
     * 计算单个候选的可解释规则分数。
     *
     * @param candidate 候选片段
     * @param keywords 查询关键词
     * @return 规则分数
     */
    public double score(RerankCandidate candidate, List<String> keywords) {
        double keywordScore = calculateKeywordScore(candidate.content(), keywords);
        double sourceScore = "knowledge".equalsIgnoreCase(candidate.sourceType()) ? KNOWLEDGE_SOURCE_BOOST : 0D;
        return candidate.retrievalScore() * RETRIEVAL_WEIGHT
                + keywordScore * KEYWORD_WEIGHT
                + sourceScore * SOURCE_WEIGHT;
    }

    private double calculateKeywordScore(String text, List<String> keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.isEmpty()) {
            return 0D;
        }
        List<String> usableKeywords = keywords.stream()
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .toList();
        if (usableKeywords.isEmpty()) {
            return 0D;
        }
        String normalizedText = text.toLowerCase();
        long hitCount = usableKeywords.stream().filter(normalizedText::contains).count();
        return (double) hitCount / usableKeywords.size();
    }
}
