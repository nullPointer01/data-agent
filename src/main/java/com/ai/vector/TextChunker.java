package com.ai.vector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量索引文本分块入口。
 *
 * @author data-agent
 */
@Component
public class TextChunker {

    private final int chunkSize;
    private final int chunkOverlap;
    private final SmartTextChunker smartTextChunker;

    @Autowired
    public TextChunker(@Value("${app.vector.chunk-size:500}") int chunkSize,
            @Value("${app.vector.chunk-overlap:100}") int chunkOverlap,
            SmartTextChunker smartTextChunker) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("app.vector.chunk-size must be greater than 0");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("app.vector.chunk-overlap must be in [0, chunk-size)");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.smartTextChunker = smartTextChunker;
    }

    TextChunker(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, new SmartTextChunker());
    }

    public List<VectorChunk> split(String sourceId, String content) {
        return smartTextChunker.split(sourceId, content, chunkSize, chunkOverlap);
    }

    public int estimateChunkCount(String content) {
        return smartTextChunker.estimateChunkCount(content, chunkSize, chunkOverlap);
    }
}
