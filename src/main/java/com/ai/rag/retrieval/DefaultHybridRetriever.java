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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

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

    private static final int MAX_QUERIES = 4;

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
        List<EmbeddingMatch<TextSegment>> vectorMatches = retrieveVectors(
                analysis, tenantId, userId, candidateTopK, minScore, sourceTypes);
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

    /** 框架生成的变体实际参与召回；顺序执行保留身份，不另建无界扇出线程池。 */
    private List<EmbeddingMatch<TextSegment>> retrieveVectors(RagQueryAnalysis analysis, String tenantId,
            String userId, int topK, double minScore, List<String> sourceTypes) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(analysis.rewrittenQuery());
        if (analysis.variants() != null) analysis.variants().stream().filter(StringUtils::hasText).forEach(queries::add);
        List<EmbeddingMatch<TextSegment>> allMatches = new ArrayList<>();
        for (String query : queries.stream().limit(MAX_QUERIES).toList()) {
            allMatches.addAll(vectorMemoryService.searchMatches(query, topK, minScore, tenantId, userId, sourceTypes));
        }
        // 同一向量空间的分数可比较；同一物理记录保留最优命中，随后只进行一次双通道 RRF。
        Map<String, EmbeddingMatch<TextSegment>> unique = new LinkedHashMap<>();
        allMatches.stream().sorted(Comparator.comparingDouble((EmbeddingMatch<TextSegment> match) -> match.score()).reversed())
                .forEach(match -> unique.putIfAbsent(match.embeddingId(), match));
        return unique.values().stream().limit(topK).toList();
    }

    private int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
