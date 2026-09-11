package com.ai.rag.retrieval;
import com.ai.rag.retrieval.RetrievalResult;
import com.ai.rag.retrieval.RrfFusionRanker;
import com.ai.rag.retrieval.HybridRetrievalResult;
import com.ai.rag.retrieval.HybridRetriever;
import com.ai.rag.RagQueryAnalysis;
import com.ai.rag.fulltext.FullTextSearchResult;
import com.ai.rag.fulltext.FullTextRetriever;

import com.ai.service.VectorMemoryService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 默认混合检索实现，通过向量检索与全文检索双通道召回，再经 RRF 融合。
 *
 * @author data-agent
 */
@Service
public class DefaultHybridRetriever implements HybridRetriever {

    private final VectorMemoryService vectorMemoryService;
    private final FullTextRetriever fullTextRetriever;
    private final RrfFusionRanker rrfFusionRanker;

    public DefaultHybridRetriever(VectorMemoryService vectorMemoryService,
            FullTextRetriever fullTextRetriever,
            RrfFusionRanker rrfFusionRanker) {
        this.vectorMemoryService = vectorMemoryService;
        this.fullTextRetriever = fullTextRetriever;
        this.rrfFusionRanker = rrfFusionRanker;
    }

    @Override
    public List<RetrievalResult> retrieve(RagQueryAnalysis analysis, String tenantId, String userId,
            int candidateTopK,
            double minScore, List<String> sourceTypes) {
        return retrieveWithTrace(analysis, tenantId, userId, candidateTopK, minScore, sourceTypes).results();
    }

    @Override
    public HybridRetrievalResult retrieveWithTrace(RagQueryAnalysis analysis, String tenantId, String userId,
            int candidateTopK,
            double minScore, List<String> sourceTypes) {
        if (analysis == null || !StringUtils.hasText(analysis.rewrittenQuery())
                || !StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
            return HybridRetrievalResult.empty();
        }
        long vectorStartedAt = System.currentTimeMillis();
        List<EmbeddingMatch<TextSegment>> vectorMatches = vectorMemoryService.searchMatches(
                analysis.rewrittenQuery(), candidateTopK, minScore, tenantId, userId, sourceTypes);
        long vectorTimeMs = System.currentTimeMillis() - vectorStartedAt;
        long fullTextStartedAt = System.currentTimeMillis();
        List<FullTextSearchResult> fullTextResults = fullTextRetriever.retrieve(analysis, tenantId, userId,
                candidateTopK, sourceTypes);
        long fullTextTimeMs = System.currentTimeMillis() - fullTextStartedAt;
        long fusionStartedAt = System.currentTimeMillis();
        List<RetrievalResult> results = rrfFusionRanker.fuse(vectorMatches, fullTextResults, candidateTopK);
        long fusionTimeMs = System.currentTimeMillis() - fusionStartedAt;
        return new HybridRetrievalResult(results, safeList(vectorMatches), safeList(fullTextResults),
                sizeOf(vectorMatches), sizeOf(fullTextResults), vectorTimeMs, fullTextTimeMs, fusionTimeMs);
    }

    private int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
