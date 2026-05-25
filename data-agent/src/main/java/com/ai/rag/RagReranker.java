package com.ai.rag;
import com.ai.rag.retrieval.RetrievalResult;

import com.ai.vector.VectorMetadataKeys;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

/**
 * 基于融合分数和关键词覆盖率的轻量重排器。
 *
 * @author data-agent
 */
@Component
public class RagReranker {

    private static final double VECTOR_WEIGHT = 0.72D;
    private static final double KEYWORD_WEIGHT = 0.2D;
    private static final double SOURCE_WEIGHT = 0.08D;
    private static final double KNOWLEDGE_SOURCE_BOOST = 0.05D;

    /**
     * 对候选片段进行重排。
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
        return matches.stream()
                .sorted(Comparator.comparingDouble((EmbeddingMatch<TextSegment> match) -> score(match, analysis))
                        .reversed())
                .toList();
    }

    /**
     * 对混合检索候选进行重排。
     *
     * @param results 检索候选
     * @param analysis 查询分析
     * @return 重排后的候选
     */
    public List<RetrievalResult> rerankHybrid(List<RetrievalResult> results, RagQueryAnalysis analysis) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .sorted(Comparator.comparingDouble((RetrievalResult result) -> score(result, analysis))
                        .reversed())
                .toList();
    }

    private double score(EmbeddingMatch<TextSegment> match, RagQueryAnalysis analysis) {
        TextSegment segment = match.embedded();
        String text = segment == null ? "" : segment.text();
        double keywordScore = calculateKeywordScore(text, analysis.keywords());
        double sourceScore = calculateSourceScore(segment);
        return match.score() * VECTOR_WEIGHT + keywordScore * KEYWORD_WEIGHT + sourceScore * SOURCE_WEIGHT;
    }

    private double score(RetrievalResult result, RagQueryAnalysis analysis) {
        double keywordScore = calculateKeywordScore(result.content(), analysis.keywords());
        double sourceScore = "knowledge".equalsIgnoreCase(result.sourceType()) ? KNOWLEDGE_SOURCE_BOOST : 0D;
        return result.score() * VECTOR_WEIGHT + keywordScore * KEYWORD_WEIGHT + sourceScore * SOURCE_WEIGHT;
    }

    private double calculateKeywordScore(String text, List<String> keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.isEmpty()) {
            return 0D;
        }
        String normalizedText = text.toLowerCase();
        long hitCount = keywords.stream()
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .filter(normalizedText::contains)
                .count();
        return (double) hitCount / keywords.size();
    }

    private double calculateSourceScore(TextSegment segment) {
        if (segment == null || segment.metadata() == null) {
            return 0D;
        }
        String type = segment.metadata().getString(VectorMetadataKeys.TYPE);
        return "knowledge".equalsIgnoreCase(type) ? KNOWLEDGE_SOURCE_BOOST : 0D;
    }
}
