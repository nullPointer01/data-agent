package com.ai.vector;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 层级分块器，负责为检索子块补充父级上下文范围。
 *
 * @author data-agent
 */
@Component
public class HierarchicalChunker {

    private static final String PARENT_ID_SEPARATOR = "_parent_";
    private static final int DEFAULT_PARENT_CONTEXT_CHARS = 1600;

    /**
     * 为子分块补充父级上下文信息。
     *
     * @param sourceId 来源文档编号
     * @param chunks 子分块
     * @return 带父级范围的子分块
     */
    public List<VectorChunk> attachParents(String sourceId, List<VectorChunk> chunks) {
        return attachParents(sourceId, chunks, DEFAULT_PARENT_CONTEXT_CHARS);
    }

    /**
     * 为子分块补充父级上下文信息。
     *
     * @param sourceId 来源文档编号
     * @param chunks 子分块
     * @param parentContextChars 父级上下文最大字符数
     * @return 带父级范围的子分块
     */
    public List<VectorChunk> attachParents(String sourceId, List<VectorChunk> chunks, int parentContextChars) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<VectorChunk> result = new ArrayList<>();
        for (VectorChunk chunk : chunks) {
            ChunkParentRange parentRange = findParentRange(chunk, chunks, parentContextChars);
            ChunkMetadata metadata = chunk.metadata().withParent(parentId(sourceId, chunk.sectionPath()),
                    parentRange.charStart(), parentRange.charEnd());
            result.add(new VectorChunk(chunk.id(), chunk.sourceId(), chunk.text(), metadata));
        }
        return result;
    }

    private ChunkParentRange findParentRange(VectorChunk target, List<VectorChunk> chunks, int parentContextChars) {
        int maxChars = Math.max(target.charEnd() - target.charStart(), parentContextChars);
        int start = target.charStart();
        int end = target.charEnd();
        String sectionPath = target.sectionPath();
        for (VectorChunk chunk : chunks) {
            if (!sameSection(sectionPath, chunk.sectionPath())) {
                continue;
            }
            int nextStart = Math.min(start, chunk.charStart());
            int nextEnd = Math.max(end, chunk.charEnd());
            if (nextEnd - nextStart <= maxChars) {
                start = nextStart;
                end = nextEnd;
            }
        }
        return new ChunkParentRange(start, end);
    }

    private boolean sameSection(String left, String right) {
        if (!StringUtils.hasText(left) && !StringUtils.hasText(right)) {
            return true;
        }
        return left != null && left.equals(right);
    }

    private String parentId(String sourceId, String sectionPath) {
        if (!StringUtils.hasText(sectionPath)) {
            return sourceId + PARENT_ID_SEPARATOR + "root";
        }
        String normalizedSection = sectionPath.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "_");
        return sourceId + PARENT_ID_SEPARATOR + normalizedSection;
    }

    private record ChunkParentRange(int charStart, int charEnd) {
    }
}
