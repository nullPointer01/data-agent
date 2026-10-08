package com.ai.vector;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import org.springframework.beans.factory.annotation.Qualifier;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 统一文档分块入口：框架递归切分文本，项目保留章节、原文位置和父级上下文。
 *
 * @author data-agent
 */
@Component
public class TextChunker {

    private final int chunkOverlap;
    private final DocumentSplitter splitter;
    private final DocumentStructureParser structureParser;
    private final HierarchicalChunker hierarchicalChunker;

    /** 配置单位为字符；overlap 是框架句级重叠上限，不是固定滑动步长。 */
    public TextChunker(@Value("${app.vector.chunk-overlap:100}") int chunkOverlap,
            @Qualifier("documentSplitter") DocumentSplitter splitter,
            DocumentStructureParser structureParser, HierarchicalChunker hierarchicalChunker) {
        this.chunkOverlap = chunkOverlap;
        this.splitter = Objects.requireNonNull(splitter);
        this.structureParser = structureParser;
        this.hierarchicalChunker = hierarchicalChunker;
    }

    /** 按连续章节分别调用框架，避免不同章节的内容混合或相互重叠。 */
    public List<VectorChunk> split(String sourceId, String content) {
        if (!hasContent(content)) return List.of();
        List<DocumentStructureSegment> structure = structureParser.parse(content);
        List<VectorChunk> chunks = new ArrayList<>();
        int first = 0;
        while (first < structure.size()) {
            int end = first + 1;
            while (end < structure.size() && structure.get(end).type() != DocumentSegmentType.HEADING
                    && Objects.equals(structure.get(first).sectionPath(), structure.get(end).sectionPath())) {
                end++;
            }
            splitSection(sourceId, content, structure.subList(first, end), chunks);
            first = end;
        }
        return hierarchicalChunker.attachParents(sourceId, chunks);
    }

    /** 数量估算与真实入库使用同一框架和同一元数据适配，避免两套切分结果。 */
    public int estimateChunkCount(String content) {
        return split("_estimate", content).size();
    }

    private void splitSection(String sourceId, String source, List<DocumentStructureSegment> section,
            List<VectorChunk> chunks) {
        int base = section.get(0).charStart();
        String sectionPath = section.get(0).sectionPath();
        String original = source.substring(base, section.get(section.size() - 1).charEnd());
        Document document = Document.from(original, new Metadata().put("sourceId", sourceId).put("sectionPath", sectionPath));
        List<TextSegment> segments = splitter.split(document).stream()
                .filter(segment -> hasContent(segment.text())).toList();
        List<ChunkSourceLocator.Range> ranges = ChunkSourceLocator.locate(original, segments, chunkOverlap);
        int coveredEnd = 0;
        for (int index = 0; index < segments.size(); index++) {
            // 去掉框架递归重复发出的纯重叠片段；正常重叠及原文中重复的内容仍保留。
            if (ranges.get(index).end() <= coveredEnd) continue;
            coveredEnd = ranges.get(index).end();
            int start = base + ranges.get(index).start();
            int end = base + ranges.get(index).end();
            List<DocumentStructureSegment> covered = section.stream()
                    .filter(part -> part.charStart() < end && part.charEnd() > start).toList();
            var metadata = new ChunkMetadata(sectionPath, start, end,
                    covered.stream().anyMatch(DocumentStructureSegment::containsTable),
                    covered.stream().anyMatch(DocumentStructureSegment::containsCode),
                    covered.stream().anyMatch(DocumentStructureSegment::containsList));
            chunks.add(new VectorChunk(sourceId + "_chunk_" + chunks.size(), sourceId, segments.get(index).text(), metadata));
        }
    }

    private static boolean hasContent(String text) {
        return text != null && text.codePoints().anyMatch(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c));
    }
}
