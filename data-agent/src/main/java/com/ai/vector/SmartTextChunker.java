package com.ai.vector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 面向向量索引的结构感知分块器。
 *
 * @author data-agent
 */
@Component
public class SmartTextChunker {

    private static final String CHUNK_ID_SEPARATOR = "_chunk_";

    private final DocumentStructureParser documentStructureParser;

    private final SemanticChunker semanticChunker;

    private final HierarchicalChunker hierarchicalChunker;

    public SmartTextChunker() {
        this(new MarkdownStructureParser(), new SemanticChunker(), new HierarchicalChunker());
    }

    @Autowired
    public SmartTextChunker(DocumentStructureParser documentStructureParser, SemanticChunker semanticChunker,
            HierarchicalChunker hierarchicalChunker) {
        this.documentStructureParser = documentStructureParser;
        this.semanticChunker = semanticChunker;
        this.hierarchicalChunker = hierarchicalChunker;
    }

    /**
     * 按文档结构和语义边界切分内容。
     *
     * @param sourceId 来源文档编号
     * @param content 原始文本
     * @param chunkSize 目标分块大小
     * @param chunkOverlap 强制切分时的重叠长度
     * @return 向量分块
     */
    public List<VectorChunk> split(String sourceId, String content, int chunkSize, int chunkOverlap) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        List<DocumentStructureSegment> segments = documentStructureParser.parse(content);
        List<VectorChunk> chunks = toChunks(sourceId, semanticChunker.chunk(segments, chunkSize, chunkOverlap));
        return hierarchicalChunker.attachParents(sourceId, chunks);
    }

    /**
     * 使用同一套分块策略估算分块数量。
     *
     * @param content 原始文本
     * @param chunkSize 目标分块大小
     * @param chunkOverlap 强制切分时的重叠长度
     * @return 估算分块数量
     */
    public int estimateChunkCount(String content, int chunkSize, int chunkOverlap) {
        return split("_estimate", content, chunkSize, chunkOverlap).size();
    }

    private List<VectorChunk> toChunks(String sourceId, List<SemanticChunk> chunks) {
        List<VectorChunk> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            SemanticChunk chunk = chunks.get(i);
            result.add(new VectorChunk(sourceId + CHUNK_ID_SEPARATOR + i, sourceId, chunk.text(),
                    chunk.metadata()));
        }
        return result;
    }
}
