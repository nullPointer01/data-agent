package com.ai.rag;
import com.ai.rag.retrieval.RetrievalChannel;
import com.ai.rag.retrieval.RetrievalResult;
import com.ai.rag.retrieval.HybridRetrievalResult;
import com.ai.rag.retrieval.HybridRetriever;

import com.ai.rag.dto.RagCitation;
import com.ai.rag.dto.RagContextResponse;
import com.ai.rag.dto.RagRetrievalTrace;
import com.ai.rag.RagReranker.RerankEvidence;
import com.ai.rag.RagReranker.RerankOutcome;
import com.ai.vector.VectorDocumentTypes;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 增强 RAG 管道，串联查询分析、混合检索、重排、上下文压缩和引用生成。
 *
 * @author data-agent
 */
@Service
public class EnhancedRagPipeline {

    private static final Set<String> RAG_SOURCE_TYPES = Set.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE);
    private static final String REFERENCE_PREFIX = "R";
    private static final int CITATION_SNIPPET_LENGTH = 240;
    private static final String VECTOR_PROVIDER = "milvus";
    private static final String COMPRESSED_MARKER = "[检索上下文已压缩]";

    private final HybridRetriever hybridRetriever;

    private final RagProperties ragProperties;

    private final RagQueryRewriter ragQueryRewriter;

    private final RagReranker ragReranker;

    private final RagContextCompressor ragContextCompressor;

    private final RagParentContextResolver ragParentContextResolver;

    private volatile RagRetrievalTrace lastTrace = RagRetrievalTrace.empty();

    public EnhancedRagPipeline(HybridRetriever hybridRetriever, RagProperties ragProperties,
            RagQueryRewriter ragQueryRewriter, RagReranker ragReranker,
            RagContextCompressor ragContextCompressor,
            RagParentContextResolver ragParentContextResolver) {
        this.hybridRetriever = hybridRetriever;
        this.ragProperties = ragProperties;
        this.ragQueryRewriter = ragQueryRewriter;
        this.ragReranker = ragReranker;
        this.ragContextCompressor = ragContextCompressor;
        this.ragParentContextResolver = ragParentContextResolver;
    }

    /**
     * 执行完整 RAG 检索流程。
     *
     * @param query 用户查询
     * @param tenantId 租户编号
     * @return RAG 上下文响应
     */
    public RagContextResponse execute(String query, String tenantId) {
        long totalStartedAt = System.currentTimeMillis();
        RagQueryAnalysis analysis = ragQueryRewriter.analyze(query);
        if (!ragProperties.isEnabled() || analysis.rewrittenQuery().isBlank() || isBlank(tenantId)) {
            return RagContextResponse.empty();
        }
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("retrieve");
        HybridRetrievalResult hybridRetrieval = hybridRetriever.retrieveWithTrace(analysis, tenantId,
                ragProperties.getCandidateTopK(), ragProperties.getMinScore(),
                List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE));
        List<RetrievalResult> matches = hybridRetrieval.results();
        stopWatch.stop();
        stopWatch.start("rerank");
        RerankOutcome rerankOutcome = ragReranker.rerankHybridWithTrace(matches, analysis);
        List<RetrievalResult> ragMatches = rerankOutcome.results().stream()
                .filter(this::isRagSource)
                .limit(ragProperties.getTopK())
                .toList();
        stopWatch.stop();
        if (ragMatches.isEmpty()) {
            return emptyResponse(analysis, hybridRetrieval, elapsed(stopWatch, "retrieve"),
                    elapsed(stopWatch, "rerank"), totalStartedAt, rerankOutcome.evidence());
        }
        stopWatch.start("parentContext");
        List<RetrievalResult> resolvedMatches = ragMatches.stream()
                .map(match -> resolveParentContext(match, tenantId))
                .toList();
        stopWatch.stop();
        stopWatch.start("compression");
        RagContextBuildResult buildResult = buildResponse(resolvedMatches);
        stopWatch.stop();
        RagContextResponse response = new RagContextResponse(buildResult.context(), buildResult.citations().size(),
                analysis.rewrittenQuery(), analysis.queryType(), analysis.keywords(), buildResult.citations());
        response.setTrace(trace(matches, resolvedMatches, response.getContext(),
                elapsed(stopWatch, "retrieve"), elapsed(stopWatch, "rerank"),
                elapsed(stopWatch, "parentContext"), elapsed(stopWatch, "compression"), totalStartedAt,
                hybridRetrieval, rerankOutcome.evidence()));
        lastTrace = response.getTrace();
        return response;
    }

    private RagContextResponse emptyResponse(RagQueryAnalysis analysis, HybridRetrievalResult hybridRetrieval,
            long retrievalTimeMs, long rerankTimeMs, long totalStartedAt, RerankEvidence rerankEvidence) {
        RagRetrievalTrace trace = trace(hybridRetrieval.results(), List.of(), "", retrievalTimeMs, rerankTimeMs,
                0L, 0L, totalStartedAt, hybridRetrieval, rerankEvidence);
        lastTrace = trace;
        return new RagContextResponse("", 0, analysis.rewrittenQuery(), analysis.queryType(), analysis.keywords(),
                List.of(), trace);
    }

    /**
     * 获取最近一次 RAG 检索链路信息。
     *
     * @return 最近一次链路信息
     */
    public RagRetrievalTrace getLastTrace() {
        return lastTrace;
    }

    private RagContextBuildResult buildResponse(List<RetrievalResult> ragMatches) {
        List<RagContextSection> sections = new ArrayList<>();
        List<RagCitation> citations = new ArrayList<>();
        for (int index = 0; index < ragMatches.size(); index++) {
            RetrievalResult match = ragMatches.get(index);
            String referenceId = REFERENCE_PREFIX + (index + 1);
            sections.add(new RagContextSection(referenceId, buildSectionHeader(referenceId, match),
                    contextContent(match)));
            citations.add(citation(referenceId, match));
        }
        String context = ragContextCompressor.compress(sections, ragProperties.getMaxContextChars());
        return new RagContextBuildResult(context, citations);
    }

    private boolean isRagSource(RetrievalResult match) {
        if (match == null) {
            return false;
        }
        return RAG_SOURCE_TYPES.contains(match.sourceType());
    }

    private RetrievalResult resolveParentContext(RetrievalResult match, String tenantId) {
        RetrievalResult resolved = ragParentContextResolver.resolve(match, tenantId);
        return resolved == null ? match : resolved;
    }

    private String buildSectionHeader(String referenceId, RetrievalResult match) {
        return "资料 [%s] [%s / %s / %s%s / score=%s]".formatted(
                referenceId, match.sourceType(), match.sourceId(), match.chunkId(), formatSection(match),
                String.format("%.2f", match.score()));
    }

    private String formatSection(RetrievalResult match) {
        if (match == null || match.sectionPath() == null || match.sectionPath().isBlank()) {
            return "";
        }
        return " / " + match.sectionPath();
    }

    private String preview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private String contextContent(RetrievalResult match) {
        if (match == null || match.parentContext() == null || match.parentContext().isBlank()) {
            return match == null ? "" : match.content();
        }
        return match.parentContext();
    }

    private RagCitation citation(String referenceId, RetrievalResult match) {
        return new RagCitation(referenceId, match.sourceType(), match.sourceId(), match.chunkId(), match.score(),
                match.vectorScore(), match.fullTextScore(), match.channels(),
                preview(match.content(), CITATION_SNIPPET_LENGTH), match.sectionPath(), match.charStart(),
                match.charEnd(), match.containsTable(), match.containsCode(), match.containsList(),
                match.parentContext() != null && !match.parentContext().isBlank());
    }

    private RagRetrievalTrace trace(List<RetrievalResult> candidates, List<RetrievalResult> finalMatches,
            String context, long retrievalTimeMs, long rerankTimeMs, long parentContextTimeMs,
            long compressionTimeMs, long totalStartedAt, HybridRetrievalResult hybridRetrieval,
            RerankEvidence rerankEvidence) {
        RerankEvidence safeEvidence = rerankEvidence == null ? RerankEvidence.empty() : rerankEvidence;
        return new RagRetrievalTrace(true, ragProperties.getFullTextProvider(), VECTOR_PROVIDER,
                ragProperties.getCandidateTopK(), ragProperties.getTopK(), ragProperties.getMaxContextChars(),
                ragProperties.getMinScore(), sizeOf(candidates), sizeOf(finalMatches),
                countChannel(candidates, RetrievalChannel.VECTOR), countChannel(candidates, RetrievalChannel.FULL_TEXT),
                hybridRetrieval.vectorRawCount(), hybridRetrieval.fullTextRawCount(), countHybrid(candidates),
                retrievalTimeMs, hybridRetrieval.vectorRetrievalTimeMs(), hybridRetrieval.fullTextRetrievalTimeMs(),
                hybridRetrieval.fusionTimeMs(), safeEvidence.provider(), safeEvidence.model(),
                safeEvidence.fallbackProvider(), safeEvidence.inputCount(), safeEvidence.outputCount(),
                safeEvidence.fallback(), safeEvidence.errorCategory(), rerankTimeMs,
                parentContextTimeMs, compressionTimeMs,
                System.currentTimeMillis() - totalStartedAt, context == null ? 0 : context.length(),
                context != null && context.contains(COMPRESSED_MARKER));
    }

    private int countChannel(List<RetrievalResult> results, String channel) {
        if (results == null || results.isEmpty()) {
            return 0;
        }
        return (int) results.stream()
                .filter(result -> result.channels() != null && result.channels().contains(channel))
                .count();
    }

    private int countHybrid(List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return 0;
        }
        return (int) results.stream()
                .filter(result -> result.channels() != null
                        && result.channels().contains(RetrievalChannel.VECTOR)
                        && result.channels().contains(RetrievalChannel.FULL_TEXT))
                .count();
    }

    private int sizeOf(List<RetrievalResult> results) {
        return results == null ? 0 : results.size();
    }

    private long elapsed(StopWatch stopWatch, String taskName) {
        for (StopWatch.TaskInfo taskInfo : stopWatch.getTaskInfo()) {
            if (taskName.equals(taskInfo.getTaskName())) {
                return taskInfo.getTimeMillis();
            }
        }
        return 0L;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record RagContextBuildResult(String context, List<RagCitation> citations) {
    }
}
