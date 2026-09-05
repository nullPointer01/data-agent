package com.ai.rag.retrieval;
import com.ai.rag.retrieval.RetrievalResult;
import com.ai.rag.fulltext.FullTextSearchResult;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.util.List;

/**
 * 混合检索结果和通道耗时。
 *
 * @param results RRF 融合后的候选
 * @param vectorResults 向量通道原始有序候选
 * @param fullTextResults 全文通道原始有序候选
 * @param vectorRawCount 向量通道原始候选数量
 * @param fullTextRawCount 全文通道原始候选数量
 * @param vectorRetrievalTimeMs 向量检索耗时
 * @param fullTextRetrievalTimeMs 全文检索耗时
 * @param fusionTimeMs RRF 融合耗时
 * @author data-agent
 */
public record HybridRetrievalResult(
        List<RetrievalResult> results,
        List<EmbeddingMatch<TextSegment>> vectorResults,
        List<FullTextSearchResult> fullTextResults,
        int vectorRawCount,
        int fullTextRawCount,
        long vectorRetrievalTimeMs,
        long fullTextRetrievalTimeMs,
        long fusionTimeMs) {

    public HybridRetrievalResult {
        results = safeList(results);
        vectorResults = safeList(vectorResults);
        fullTextResults = safeList(fullTextResults);
    }

    /**
     * 返回空混合检索结果。
     *
     * @return 空混合检索结果
     */
    public static HybridRetrievalResult empty() {
        return new HybridRetrievalResult(List.of(), List.of(), List.of(), 0, 0, 0L, 0L, 0L);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
