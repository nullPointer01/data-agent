package com.ai.rag.fulltext;
import com.ai.rag.fulltext.FullTextSearchResult;
import com.ai.rag.fulltext.FullTextRetriever;
import com.ai.rag.RagQueryAnalysis;

import com.ai.model.FileMetadata;
import com.ai.model.FileProcessingStatus;
import com.ai.model.KnowledgeEntry;
import com.ai.repository.FileMetadataRepository;
import com.ai.repository.KnowledgeEntryRepository;
import com.ai.vector.TextChunker;
import com.ai.vector.VectorChunk;
import com.ai.vector.VectorDocumentTypes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 基于 JPA 的全文检索兜底实现。
 *
 * <p>该实现用于在 Elasticsearch 尚未接入时提供可用的全文召回能力，生产环境可以通过新增
 * {@link FullTextRetriever} 实现替换为 ES/BM25。</p>
 *
 * @author data-agent
 */
@Component
@ConditionalOnProperty(name = "app.rag.full-text-provider", havingValue = "jpa", matchIfMissing = true)
public class JpaFullTextRetriever implements FullTextRetriever {

    private static final int MAX_TERMS = 8;
    private static final int MAX_CHUNKS_PER_DOCUMENT = 3;
    private static final int MIN_RESULT_LIMIT = 1;
    private static final double TERM_SCORE_WEIGHT = 0.55D;
    private static final double KEYWORD_SCORE_WEIGHT = 0.35D;
    private static final double SOURCE_SCORE_WEIGHT = 0.1D;
    private static final double KNOWLEDGE_SOURCE_BOOST = 0.08D;
    private static final double FILE_SOURCE_BOOST = 0.04D;
    private static final String KNOWLEDGE_TITLE_PREFIX = "知识标题: ";
    private static final String DESCRIPTION_PREFIX = "\n描述: ";
    private static final String CONTENT_PREFIX = "\n内容: ";
    private static final String FILE_NAME_PREFIX = "文件名: ";

    private final KnowledgeEntryRepository knowledgeRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final TextChunker textChunker;

    public JpaFullTextRetriever(KnowledgeEntryRepository knowledgeRepository,
            FileMetadataRepository fileMetadataRepository,
            TextChunker textChunker) {
        this.knowledgeRepository = knowledgeRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.textChunker = textChunker;
    }

    @Override
    public List<FullTextSearchResult> retrieve(RagQueryAnalysis analysis, String tenantId, int topK,
            List<String> sourceTypes) {
        if (analysis == null || !StringUtils.hasText(tenantId) || topK <= 0) {
            return List.of();
        }
        List<String> terms = buildTerms(analysis);
        if (terms.isEmpty()) {
            return List.of();
        }
        Set<String> typeFilter = new LinkedHashSet<>(sourceTypes == null ? List.of() : sourceTypes);
        Map<String, FullTextSearchResult> resultMap = new LinkedHashMap<>();
        int limit = Math.max(MIN_RESULT_LIMIT, topK);
        for (String term : terms) {
            searchKnowledge(analysis, tenantId, term, limit, typeFilter, resultMap);
            searchFiles(analysis, tenantId, term, limit, typeFilter, resultMap);
        }
        return resultMap.values().stream()
                .sorted(Comparator.comparingDouble(FullTextSearchResult::score).reversed())
                .limit(limit)
                .toList();
    }

    private List<String> buildTerms(RagQueryAnalysis analysis) {
        Set<String> terms = new LinkedHashSet<>();
        addTerm(terms, analysis.rewrittenQuery());
        if (analysis.variants() != null) {
            analysis.variants().forEach(term -> addTerm(terms, term));
        }
        if (analysis.keywords() != null) {
            analysis.keywords().forEach(term -> addTerm(terms, term));
        }
        return terms.stream().limit(MAX_TERMS).toList();
    }

    private void addTerm(Set<String> terms, String term) {
        if (StringUtils.hasText(term)) {
            terms.add(term.trim().toLowerCase(Locale.ROOT));
        }
    }

    private void searchKnowledge(RagQueryAnalysis analysis, String tenantId, String term, int limit,
            Set<String> typeFilter, Map<String, FullTextSearchResult> resultMap) {
        if (!shouldSearch(typeFilter, VectorDocumentTypes.KNOWLEDGE)) {
            return;
        }
        PageRequest page = PageRequest.of(0, limit);
        List<KnowledgeEntry> entries = knowledgeRepository.searchByTenantAndKeyword(tenantId, likePattern(term), page);
        for (KnowledgeEntry entry : entries) {
            collectKnowledgeResults(entry, analysis, term, resultMap);
        }
    }

    private void searchFiles(RagQueryAnalysis analysis, String tenantId, String term, int limit,
            Set<String> typeFilter, Map<String, FullTextSearchResult> resultMap) {
        if (!shouldSearch(typeFilter, VectorDocumentTypes.FILE)) {
            return;
        }
        PageRequest page = PageRequest.of(0, limit);
        List<FileMetadata> files = fileMetadataRepository.searchByTenantAndKeyword(tenantId,
                FileProcessingStatus.COMPLETED.name(), likePattern(term), page);
        for (FileMetadata file : files) {
            collectFileResults(file, analysis, term, resultMap);
        }
    }

    private boolean shouldSearch(Set<String> typeFilter, String sourceType) {
        return typeFilter.isEmpty() || typeFilter.contains(sourceType);
    }

    private void collectKnowledgeResults(KnowledgeEntry entry, RagQueryAnalysis analysis, String term,
            Map<String, FullTextSearchResult> resultMap) {
        List<FullTextSearchResult> candidates = split(entry.getKnowledgeId(), entry.getContent()).stream()
                .map(chunk -> toKnowledgeResult(entry, chunk, analysis, term))
                .filter(result -> result.score() > 0D)
                .sorted(Comparator.comparingDouble(FullTextSearchResult::score).reversed())
                .limit(MAX_CHUNKS_PER_DOCUMENT)
                .toList();
        candidates.forEach(result -> mergeResult(resultMap, result));
    }

    private void collectFileResults(FileMetadata file, RagQueryAnalysis analysis, String term,
            Map<String, FullTextSearchResult> resultMap) {
        List<FullTextSearchResult> candidates = split(file.getFileId(), file.getContent()).stream()
                .map(chunk -> toFileResult(file, chunk, analysis, term))
                .filter(result -> result.score() > 0D)
                .sorted(Comparator.comparingDouble(FullTextSearchResult::score).reversed())
                .limit(MAX_CHUNKS_PER_DOCUMENT)
                .toList();
        candidates.forEach(result -> mergeResult(resultMap, result));
    }

    private List<VectorChunk> split(String sourceId, String content) {
        if (!StringUtils.hasText(content)) {
            return List.of(new VectorChunk(sourceId + "_chunk_0", sourceId, ""));
        }
        return textChunker.split(sourceId, content);
    }

    private FullTextSearchResult toKnowledgeResult(KnowledgeEntry entry, VectorChunk chunk,
            RagQueryAnalysis analysis, String term) {
        String content = buildKnowledgeContent(entry, chunk.text());
        double score = score(content, analysis, term, KNOWLEDGE_SOURCE_BOOST);
        return new FullTextSearchResult(VectorDocumentTypes.KNOWLEDGE, entry.getKnowledgeId(), chunk.id(),
                content, score, chunk.sectionPath(), chunk.charStart(), chunk.charEnd(),
                chunk.containsTable(), chunk.containsCode(), chunk.containsList());
    }

    private FullTextSearchResult toFileResult(FileMetadata file, VectorChunk chunk,
            RagQueryAnalysis analysis, String term) {
        String content = FILE_NAME_PREFIX + getText(file.getFilename()) + CONTENT_PREFIX + getText(chunk.text());
        double score = score(content, analysis, term, FILE_SOURCE_BOOST);
        return new FullTextSearchResult(VectorDocumentTypes.FILE, file.getFileId(), chunk.id(), content, score,
                chunk.sectionPath(), chunk.charStart(), chunk.charEnd(), chunk.containsTable(),
                chunk.containsCode(), chunk.containsList());
    }

    private String buildKnowledgeContent(KnowledgeEntry entry, String chunkText) {
        StringBuilder builder = new StringBuilder();
        builder.append(KNOWLEDGE_TITLE_PREFIX).append(getText(entry.getName()));
        if (StringUtils.hasText(entry.getDescription())) {
            builder.append(DESCRIPTION_PREFIX).append(entry.getDescription());
        }
        if (StringUtils.hasText(chunkText)) {
            builder.append(CONTENT_PREFIX).append(chunkText);
        }
        return builder.toString();
    }

    private double score(String content, RagQueryAnalysis analysis, String term, double sourceBoost) {
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        double termScore = normalizedContent.contains(term) ? 1D : 0D;
        double keywordScore = keywordCoverage(normalizedContent, analysis.keywords());
        return Math.min(1D, termScore * TERM_SCORE_WEIGHT + keywordScore * KEYWORD_SCORE_WEIGHT
                + sourceBoost * SOURCE_SCORE_WEIGHT);
    }

    private double keywordCoverage(String normalizedContent, List<String> keywords) {
        if (!StringUtils.hasText(normalizedContent) || keywords == null || keywords.isEmpty()) {
            return 0D;
        }
        long hitCount = keywords.stream()
                .filter(StringUtils::hasText)
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .filter(normalizedContent::contains)
                .count();
        return (double) hitCount / keywords.size();
    }

    private void mergeResult(Map<String, FullTextSearchResult> resultMap, FullTextSearchResult candidate) {
        String key = keyOf(candidate.sourceType(), candidate.sourceId(), candidate.chunkId());
        FullTextSearchResult previous = resultMap.get(key);
        if (previous == null || candidate.score() > previous.score()) {
            resultMap.put(key, candidate);
        }
    }

    private String keyOf(String sourceType, String sourceId, String chunkId) {
        return sourceType + "::" + sourceId + "::" + chunkId;
    }

    private String escapeLike(String term) {
        return term.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private String likePattern(String term) {
        return "%" + escapeLike(term) + "%";
    }

    private String getText(String value) {
        return value == null ? "" : value;
    }
}
