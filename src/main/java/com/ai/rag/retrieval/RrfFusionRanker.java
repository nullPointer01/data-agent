package com.ai.rag.retrieval;
import com.ai.rag.retrieval.RetrievalResult;
import com.ai.rag.retrieval.RetrievalChannel;
import com.ai.rag.fulltext.FullTextSearchResult;

import com.ai.vector.ChunkMetadata;
import com.ai.vector.VectorMetadataKeys;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 使用 RRF（Reciprocal Rank Fusion）融合向量检索与全文检索结果。
 *
 * @author data-agent
 */
@Component
public class RrfFusionRanker {

    private static final double RANK_CONSTANT = 60D;
    private static final double RAW_SCORE_WEIGHT = 0.05D;

    /**
     * 融合向量与全文候选。
     *
     * @param vectorMatches 向量候选
     * @param fullTextResults 全文候选
     * @param topK 返回数量
     * @return 混合检索结果
     */
    public List<RetrievalResult> fuse(List<EmbeddingMatch<TextSegment>> vectorMatches,
            List<FullTextSearchResult> fullTextResults, int topK) {
        if (topK <= 0) {
            return List.of();
        }
        Map<String, FusionCandidate> candidates = new LinkedHashMap<>();
        addVectorMatches(candidates, vectorMatches);
        addFullTextResults(candidates, fullTextResults);
        return candidates.values().stream()
                .map(this::toResult)
                .sorted(Comparator.comparingDouble(RetrievalResult::score).reversed())
                .limit(topK)
                .toList();
    }

    private void addVectorMatches(Map<String, FusionCandidate> candidates,
            List<EmbeddingMatch<TextSegment>> vectorMatches) {
        if (vectorMatches == null || vectorMatches.isEmpty()) {
            return;
        }
        for (int index = 0; index < vectorMatches.size(); index++) {
            EmbeddingMatch<TextSegment> match = vectorMatches.get(index);
            VectorResult vectorResult = VectorResult.from(match);
            if (!vectorResult.isValid()) {
                continue;
            }
            FusionCandidate candidate = candidates.computeIfAbsent(vectorResult.key(), key -> FusionCandidate.of(
                    vectorResult.sourceType(), vectorResult.sourceId(), vectorResult.chunkId()));
            candidate.addVector(vectorResult, match.score(), rrfContribution(index + 1));
        }
    }

    private void addFullTextResults(Map<String, FusionCandidate> candidates,
            List<FullTextSearchResult> fullTextResults) {
        if (fullTextResults == null || fullTextResults.isEmpty()) {
            return;
        }
        for (int index = 0; index < fullTextResults.size(); index++) {
            FullTextSearchResult result = fullTextResults.get(index);
            if (!StringUtils.hasText(result.sourceType()) || !StringUtils.hasText(result.sourceId())
                    || !StringUtils.hasText(result.chunkId())) {
                continue;
            }
            String key = keyOf(result.sourceType(), result.sourceId(), result.chunkId());
            FusionCandidate candidate = candidates.computeIfAbsent(key, ignored -> FusionCandidate.of(
                    result.sourceType(), result.sourceId(), result.chunkId()));
            candidate.addFullText(result, rrfContribution(index + 1));
        }
    }

    private RetrievalResult toResult(FusionCandidate candidate) {
        double maxRrfScore = 2D / (RANK_CONSTANT + 1D);
        double rawScore = Math.max(candidate.vectorScore == null ? 0D : candidate.vectorScore,
                candidate.fullTextScore == null ? 0D : candidate.fullTextScore);
        double normalizedScore = Math.min(1D, candidate.rrfScore / maxRrfScore + rawScore * RAW_SCORE_WEIGHT);
        return new RetrievalResult(candidate.sourceType, candidate.sourceId, candidate.chunkId,
                candidate.content, normalizedScore, candidate.vectorScore, candidate.fullTextScore,
                List.copyOf(candidate.channels), candidate.metadata(), candidate.parentContext);
    }

    private double rrfContribution(int rank) {
        return 1D / (RANK_CONSTANT + rank);
    }

    private static String keyOf(String sourceType, String sourceId, String chunkId) {
        return sourceType + "::" + sourceId + "::" + chunkId;
    }

    private static final class FusionCandidate {

        private final String sourceType;
        private final String sourceId;
        private final String chunkId;
        private final Set<String> channels = new LinkedHashSet<>();
        private String content = "";
        private double rrfScore;
        private Double vectorScore;
        private Double fullTextScore;
        private String sectionPath = "";
        private int charStart;
        private int charEnd;
        private boolean containsTable;
        private boolean containsCode;
        private boolean containsList;
        private String parentChunkId = "";
        private int parentCharStart;
        private int parentCharEnd;
        private String parentContext = "";

        private FusionCandidate(String sourceType, String sourceId, String chunkId) {
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.chunkId = chunkId;
        }

        private static FusionCandidate of(String sourceType, String sourceId, String chunkId) {
            return new FusionCandidate(sourceType, sourceId, chunkId);
        }

        private void addVector(VectorResult vectorResult, double score, double contribution) {
            channels.add(RetrievalChannel.VECTOR);
            vectorScore = score;
            rrfScore += contribution;
            if (StringUtils.hasText(vectorResult.content())) {
                content = vectorResult.content();
            }
            applyMetadata(vectorResult.sectionPath(), vectorResult.charStart(), vectorResult.charEnd(),
                    vectorResult.containsTable(), vectorResult.containsCode(), vectorResult.containsList(),
                    vectorResult.parentChunkId(), vectorResult.parentCharStart(), vectorResult.parentCharEnd());
        }

        private void addFullText(FullTextSearchResult result, double contribution) {
            channels.add(RetrievalChannel.FULL_TEXT);
            fullTextScore = result.score();
            rrfScore += contribution;
            if (!StringUtils.hasText(content) && StringUtils.hasText(result.content())) {
                content = result.content();
            }
            if (!StringUtils.hasText(parentContext) && StringUtils.hasText(result.parentContext())) {
                parentContext = result.parentContext();
            }
            applyMetadata(result.sectionPath(), result.charStart(), result.charEnd(), result.containsTable(),
                    result.containsCode(), result.containsList(), result.metadata().parentChunkId(),
                    result.metadata().parentCharStart(), result.metadata().parentCharEnd());
        }

        private void applyMetadata(String nextSectionPath, int nextCharStart, int nextCharEnd,
                boolean nextContainsTable, boolean nextContainsCode, boolean nextContainsList,
                String nextParentChunkId, int nextParentCharStart, int nextParentCharEnd) {
            if (!StringUtils.hasText(sectionPath) && StringUtils.hasText(nextSectionPath)) {
                sectionPath = nextSectionPath;
            }
            if (charStart == 0 && nextCharStart > 0) {
                charStart = nextCharStart;
            }
            if (charEnd == 0 && nextCharEnd > 0) {
                charEnd = nextCharEnd;
            }
            containsTable = containsTable || nextContainsTable;
            containsCode = containsCode || nextContainsCode;
            containsList = containsList || nextContainsList;
            if (!StringUtils.hasText(parentChunkId) && StringUtils.hasText(nextParentChunkId)) {
                parentChunkId = nextParentChunkId;
            }
            if (parentCharStart == 0 && nextParentCharStart > 0) {
                parentCharStart = nextParentCharStart;
            }
            if (parentCharEnd == 0 && nextParentCharEnd > 0) {
                parentCharEnd = nextParentCharEnd;
            }
        }

        private ChunkMetadata metadata() {
            return new ChunkMetadata(sectionPath, charStart, charEnd, containsTable, containsCode, containsList,
                    parentChunkId, parentCharStart, parentCharEnd);
        }
    }

    private record VectorResult(String sourceType,
            String sourceId,
            String chunkId,
            String content,
            String sectionPath,
            int charStart,
            int charEnd,
            boolean containsTable,
            boolean containsCode,
            boolean containsList,
            String parentChunkId,
            int parentCharStart,
            int parentCharEnd) {

        private static VectorResult from(EmbeddingMatch<TextSegment> match) {
            if (match == null || match.embedded() == null || match.embedded().metadata() == null) {
                return new VectorResult(null, null, null, null, "", 0, 0, false, false, false, "", 0, 0);
            }
            TextSegment segment = match.embedded();
            String sourceType = segment.metadata().getString(VectorMetadataKeys.TYPE);
            String chunkId = segment.metadata().getString(VectorMetadataKeys.ID);
            String sourceId = segment.metadata().getString(VectorMetadataKeys.SOURCE_ID);
            if (!StringUtils.hasText(sourceId)) {
                sourceId = chunkId;
            }
            return new VectorResult(sourceType, sourceId, chunkId, segment.text(),
                    getMetadata(segment, VectorMetadataKeys.SECTION_PATH),
                    parseInt(getMetadata(segment, VectorMetadataKeys.CHAR_START)),
                    parseInt(getMetadata(segment, VectorMetadataKeys.CHAR_END)),
                    parseBoolean(getMetadata(segment, VectorMetadataKeys.CONTAINS_TABLE)),
                    parseBoolean(getMetadata(segment, VectorMetadataKeys.CONTAINS_CODE)),
                    parseBoolean(getMetadata(segment, VectorMetadataKeys.CONTAINS_LIST)),
                    getMetadata(segment, VectorMetadataKeys.PARENT_CHUNK_ID),
                    parseInt(getMetadata(segment, VectorMetadataKeys.PARENT_CHAR_START)),
                    parseInt(getMetadata(segment, VectorMetadataKeys.PARENT_CHAR_END)));
        }

        private boolean isValid() {
            return StringUtils.hasText(sourceType) && StringUtils.hasText(sourceId)
                    && StringUtils.hasText(chunkId);
        }

        private String key() {
            return keyOf(sourceType, sourceId, chunkId);
        }

        private static String getMetadata(TextSegment segment, String key) {
            return segment.metadata().getString(key);
        }

        private static int parseInt(String value) {
            if (!StringUtils.hasText(value)) {
                return 0;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private static boolean parseBoolean(String value) {
            return Boolean.parseBoolean(value);
        }
    }
}
