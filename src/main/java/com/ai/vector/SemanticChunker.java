package com.ai.vector;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义分块器，负责按句子、结构边界和大小限制生成模型友好的片段。
 *
 * @author data-agent
 */
@Component
public class SemanticChunker {

    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^。！？.!?；;]+[。！？.!?；;]?");
    private static final Pattern SENTENCE_SEPARATOR = Pattern.compile("(?<=[。！？.!?；;])\\s*");
    private static final String MARKDOWN_HEADING_PREFIX = "#";
    private static final String LINE_SEPARATOR = "\n";

    /**
     * 基于文档结构片段生成语义分块。
     *
     * @param segments 文档结构片段
     * @param chunkSize 目标分块大小
     * @param chunkOverlap 强制切分时的重叠长度
     * @return 语义分块
     */
    public List<SemanticChunk> chunk(List<DocumentStructureSegment> segments, int chunkSize, int chunkOverlap) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        List<SemanticChunk> semanticSegments = splitParagraphs(segments);
        List<SemanticChunk> merged = mergeSegments(semanticSegments, chunkSize);
        return splitOversizedChunks(merged, chunkSize, chunkOverlap);
    }

    private List<SemanticChunk> splitParagraphs(List<DocumentStructureSegment> segments) {
        List<SemanticChunk> chunks = new ArrayList<>();
        for (DocumentStructureSegment segment : segments) {
            if (DocumentSegmentType.PARAGRAPH == segment.type()) {
                addParagraphSegments(chunks, segment.text(), segment.sectionPath(), segment.charStart());
                continue;
            }
            chunks.add(new SemanticChunk(segment.text(), segment.sectionPath(), segment.charStart(),
                    segment.charEnd(), segment.containsTable(), segment.containsCode(), segment.containsList()));
        }
        return chunks;
    }

    private void addParagraphSegments(List<SemanticChunk> chunks, String paragraph, String sectionPath,
            int paragraphStart) {
        Matcher matcher = SENTENCE_PATTERN.matcher(paragraph);
        boolean matched = false;
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (StringUtils.hasText(sentence)) {
                int start = paragraphStart + matcher.start();
                int end = paragraphStart + matcher.end();
                chunks.add(new SemanticChunk(sentence, sectionPath, start, end, false, false, false));
                matched = true;
            }
        }
        if (!matched && StringUtils.hasText(paragraph)) {
            chunks.add(new SemanticChunk(paragraph.trim(), sectionPath, paragraphStart,
                    paragraphStart + paragraph.length(), false, false, false));
        }
    }

    private List<SemanticChunk> mergeSegments(List<SemanticChunk> segments, int chunkSize) {
        List<SemanticChunk> chunks = new ArrayList<>();
        SemanticChunk current = null;
        for (SemanticChunk segment : segments) {
            if (isHeading(segment.text()) && current != null) {
                chunks.add(current);
                current = null;
            }
            if (current == null) {
                current = segment;
                continue;
            }
            if (current.text().length() + segment.text().length() + 1 <= chunkSize) {
                current = current.merge(segment);
            } else {
                chunks.add(current);
                current = segment;
            }
        }
        if (current != null) {
            chunks.add(current);
        }
        return chunks;
    }

    private List<SemanticChunk> splitOversizedChunks(List<SemanticChunk> chunks, int chunkSize, int chunkOverlap) {
        List<SemanticChunk> result = new ArrayList<>();
        for (SemanticChunk chunk : chunks) {
            if (chunk.text().length() <= chunkSize) {
                result.add(chunk);
            } else if (chunk.text().contains(LINE_SEPARATOR)) {
                result.addAll(splitByLines(chunk, chunkSize, chunkOverlap));
            } else {
                result.addAll(forceSplit(chunk, chunkSize, chunkOverlap));
            }
        }
        return result;
    }

    private List<SemanticChunk> splitByLines(SemanticChunk segment, int chunkSize, int chunkOverlap) {
        List<SemanticChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentStart = segment.charStart();
        int offset = 0;
        for (String line : segment.text().split(LINE_SEPARATOR)) {
            if (line.length() > chunkSize) {
                flushChunk(chunks, current, segment, currentStart);
                chunks.addAll(forceSplit(segment.slice(line, offset, offset + line.length()), chunkSize, chunkOverlap));
                offset += line.length() + 1;
                continue;
            }
            if (current.isEmpty()) {
                current.append(line);
                currentStart = segment.charStart() + offset;
                offset += line.length() + 1;
                continue;
            }
            if (current.length() + line.length() + 1 <= chunkSize) {
                current.append(LINE_SEPARATOR).append(line);
                offset += line.length() + 1;
            } else {
                flushChunk(chunks, current, segment, currentStart);
                current.append(line);
                currentStart = segment.charStart() + offset;
                offset += line.length() + 1;
            }
        }
        flushChunk(chunks, current, segment, currentStart);
        return chunks;
    }

    private void flushChunk(List<SemanticChunk> chunks, StringBuilder current, SemanticChunk source,
            int charStart) {
        if (StringUtils.hasText(current)) {
            String text = current.toString().trim();
            chunks.add(source.withText(text, charStart, charStart + text.length()));
            current.setLength(0);
        }
    }

    private List<SemanticChunk> forceSplit(SemanticChunk segment, int chunkSize, int chunkOverlap) {
        List<SemanticChunk> chunks = new ArrayList<>();
        int position = 0;
        while (position < segment.text().length()) {
            int hardEnd = Math.min(position + chunkSize, segment.text().length());
            int end = findSemanticBoundary(segment.text(), position, hardEnd, chunkSize);
            String chunk = segment.text().substring(position, end).trim();
            if (StringUtils.hasText(chunk)) {
                chunks.add(segment.withText(chunk, segment.charStart() + position, segment.charStart() + end));
            }
            if (end == segment.text().length()) {
                break;
            }
            position = Math.max(position + 1, end - chunkOverlap);
        }
        return chunks;
    }

    private int findSemanticBoundary(String content, int start, int hardEnd, int chunkSize) {
        int minEnd = start + Math.max(1, chunkSize / 2);
        for (int index = hardEnd; index > minEnd; index--) {
            if (isSentenceBoundary(content.charAt(index - 1))) {
                return index;
            }
        }
        return hardEnd;
    }

    private boolean isHeading(String line) {
        return line.trim().startsWith(MARKDOWN_HEADING_PREFIX);
    }

    private boolean isSentenceBoundary(char value) {
        return SENTENCE_SEPARATOR.matcher(String.valueOf(value)).matches();
    }
}
