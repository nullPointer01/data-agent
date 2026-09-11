package com.ai.rag.eval;

import com.ai.rag.dto.RagCitation;
import com.ai.rag.dto.RagContextResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 按有标注和无标注两种口径评估 RAG 检索相关性。
 *
 * <p>有标注评测使用期望关键词覆盖率；临时检索没有标注时，使用最佳引用的向量相似度，
 * 避免把未经中文分词的完整问句当成关键词做字面匹配。</p>
 *
 * @author data-agent
 */
@Component
public class RagRelevanceEvaluator {

    private static final int HUNDRED_PERCENT = 100;

    /**
     * 评估一次检索结果的相关性。
     *
     * @param expectedKeywords 人工标注的期望关键词；为空时使用向量相关性
     * @param response RAG 检索响应
     * @return 相关性评估结果和评分依据
     */
    public RelevanceEvaluation evaluate(List<String> expectedKeywords, RagContextResponse response) {
        List<String> normalizedKeywords = normalizeTerms(expectedKeywords);
        RagContextResponse safeResponse = response == null ? RagContextResponse.empty() : response;
        if (!normalizedKeywords.isEmpty()) {
            return evaluateKeywordCoverage(normalizedKeywords, safeResponse);
        }
        return evaluateVectorSimilarity(safeResponse);
    }

    private RelevanceEvaluation evaluateKeywordCoverage(List<String> expectedKeywords,
            RagContextResponse response) {
        String searchableText = searchableText(response);
        List<String> matchedKeywords = expectedKeywords.stream()
                .filter(keyword -> searchableText.contains(keyword))
                .toList();
        double score = ratio(matchedKeywords.size(), expectedKeywords.size());
        return new RelevanceEvaluation(score, expectedKeywords, matchedKeywords,
                "命中标注关键词 %d/%d".formatted(matchedKeywords.size(), expectedKeywords.size()));
    }

    private RelevanceEvaluation evaluateVectorSimilarity(RagContextResponse response) {
        RagCitation bestCitation = safeList(response.getCitations()).stream()
                .filter(citation -> citation != null && citation.vectorScore() != null)
                .filter(citation -> Double.isFinite(citation.vectorScore()))
                .max(Comparator.comparingDouble(RagCitation::vectorScore))
                .orElse(null);
        if (bestCitation == null) {
            return new RelevanceEvaluation(0D, List.of(), List.of(),
                    "未提供标注关键词，且引用缺少向量相似度");
        }
        double score = clamp(bestCitation.vectorScore());
        String reference = StringUtils.hasText(bestCitation.referenceId())
                ? bestCitation.referenceId()
                : bestCitation.chunkId();
        return new RelevanceEvaluation(score, List.of(), List.of(),
                "最佳向量相似度 %d%%（%s）".formatted(Math.round(score * HUNDRED_PERCENT), reference));
    }

    private String searchableText(RagContextResponse response) {
        StringBuilder builder = new StringBuilder(blankToEmpty(response.getContext()));
        for (RagCitation citation : safeList(response.getCitations())) {
            if (citation == null) {
                continue;
            }
            builder.append('\n')
                    .append(blankToEmpty(citation.sourceType()))
                    .append(' ')
                    .append(blankToEmpty(citation.sourceId()))
                    .append(' ')
                    .append(blankToEmpty(citation.sourceName()))
                    .append(' ')
                    .append(blankToEmpty(citation.chunkId()))
                    .append(' ')
                    .append(blankToEmpty(citation.sectionPath()))
                    .append(' ')
                    .append(blankToEmpty(citation.snippet()));
        }
        return normalizeForMatch(builder.toString());
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

    private String normalizeForMatch(String value) {
        return blankToEmpty(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[，。！？；：,.!?;:()（）\\[\\]【】\"'`]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double ratio(int numerator, int denominator) {
        return denominator <= 0 ? 0D : Math.min(1D, (double) numerator / denominator);
    }

    private double clamp(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 相关性分数、标注覆盖和可展示的评分依据。
     *
     * @param score 相关性分数
     * @param expectedKeywords 归一化后的标注关键词
     * @param matchedKeywords 已命中的标注关键词
     * @param detail 评分依据
     */
    public record RelevanceEvaluation(
            double score,
            List<String> expectedKeywords,
            List<String> matchedKeywords,
            String detail) {

        public RelevanceEvaluation {
            expectedKeywords = expectedKeywords == null ? List.of() : List.copyOf(expectedKeywords);
            matchedKeywords = matchedKeywords == null ? List.of() : List.copyOf(matchedKeywords);
            detail = detail == null ? "" : detail;
        }
    }
}
