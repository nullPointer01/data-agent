package com.ai.vector;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 结构解析器。
 *
 * @author data-agent
 */
@Component
public class MarkdownStructureParser implements DocumentStructureParser {

    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\s*(\\d+\\.|\\d+、|[一二三四五六七八九十]+、)\\s+.*");
    private static final Pattern UNORDERED_LIST_PATTERN = Pattern.compile("^\\s*[-*+]\\s+.*");
    private static final Pattern TABLE_SEPARATOR_PATTERN =
            Pattern.compile("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final String MARKDOWN_HEADING_PREFIX = "#";
    private static final String CODE_FENCE_BACKTICK = "```";
    private static final String CODE_FENCE_TILDE = "~~~";
    private static final String LINE_SEPARATOR = "\n";
    private static final int HEADING_DEPTH = 6;

    @Override
    public List<DocumentStructureSegment> parse(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        return splitIntoSegments(normalizeLineEndings(content));
    }

    private List<DocumentStructureSegment> splitIntoSegments(String content) {
        List<DocumentStructureSegment> segments = new ArrayList<>();
        List<LinePart> buffer = new ArrayList<>();
        String[] sectionStack = new String[HEADING_DEPTH];
        DocumentSegmentType currentType = DocumentSegmentType.PARAGRAPH;
        int cursor = 0;
        for (String rawLine : content.split(LINE_SEPARATOR, -1)) {
            String line = rawLine.stripTrailing();
            int lineStart = cursor;
            int lineEnd = lineStart + line.length();
            cursor += rawLine.length() + 1;
            if (currentType == DocumentSegmentType.CODE) {
                buffer.add(new LinePart(line, lineStart, lineEnd));
                if (isCodeFence(line)) {
                    flushSegments(segments, buffer, currentType, currentSectionPath(sectionStack));
                    currentType = DocumentSegmentType.PARAGRAPH;
                }
                continue;
            }
            if (!StringUtils.hasText(line)) {
                flushSegments(segments, buffer, currentType, currentSectionPath(sectionStack));
                currentType = DocumentSegmentType.PARAGRAPH;
                continue;
            }
            DocumentSegmentType nextType = classifyLine(line, currentType);
            if (nextType == DocumentSegmentType.CODE || nextType == DocumentSegmentType.HEADING) {
                flushSegments(segments, buffer, currentType, currentSectionPath(sectionStack));
                if (nextType == DocumentSegmentType.HEADING) {
                    updateSectionStack(sectionStack, line);
                }
                buffer.add(new LinePart(line.trim(), lineStart, lineEnd));
                currentType = nextType;
                if (nextType == DocumentSegmentType.HEADING) {
                    flushSegments(segments, buffer, currentType, currentSectionPath(sectionStack));
                    currentType = DocumentSegmentType.PARAGRAPH;
                }
                continue;
            }
            if (shouldStartNewSegment(currentType, nextType)) {
                flushSegments(segments, buffer, currentType, currentSectionPath(sectionStack));
            }
            buffer.add(new LinePart(line.trim(), lineStart, lineEnd));
            currentType = nextType;
        }
        flushSegments(segments, buffer, currentType, currentSectionPath(sectionStack));
        return segments;
    }

    private DocumentSegmentType classifyLine(String line, DocumentSegmentType currentType) {
        if (isCodeFence(line)) {
            return DocumentSegmentType.CODE;
        }
        if (isHeading(line)) {
            return DocumentSegmentType.HEADING;
        }
        if (isTableLine(line)) {
            return DocumentSegmentType.TABLE;
        }
        if (isListLine(line) || currentType == DocumentSegmentType.LIST && isListContinuation(line)) {
            return DocumentSegmentType.LIST;
        }
        return DocumentSegmentType.PARAGRAPH;
    }

    private boolean shouldStartNewSegment(DocumentSegmentType currentType, DocumentSegmentType nextType) {
        if (currentType == nextType) {
            return false;
        }
        return !isEmptyCompatible(currentType) || !isEmptyCompatible(nextType);
    }

    private boolean isEmptyCompatible(DocumentSegmentType type) {
        return DocumentSegmentType.PARAGRAPH == type;
    }

    private void flushSegments(List<DocumentStructureSegment> segments, List<LinePart> buffer,
            DocumentSegmentType type, String sectionPath) {
        if (buffer.isEmpty()) {
            return;
        }
        String text = buffer.stream().map(LinePart::text).reduce((left, right) -> left + LINE_SEPARATOR + right)
                .orElse("").trim();
        int charStart = buffer.get(0).charStart();
        int charEnd = buffer.get(buffer.size() - 1).charEnd();
        buffer.clear();
        if (StringUtils.hasText(text)) {
            segments.add(new DocumentStructureSegment(text, sectionPath, charStart, charEnd, type));
        }
    }

    private String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private boolean isHeading(String line) {
        return line.trim().startsWith(MARKDOWN_HEADING_PREFIX);
    }

    private boolean isCodeFence(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith(CODE_FENCE_BACKTICK) || trimmed.startsWith(CODE_FENCE_TILDE);
    }

    private boolean isTableLine(String line) {
        String trimmed = line.trim();
        return trimmed.contains("|") && (trimmed.startsWith("|") || TABLE_SEPARATOR_PATTERN.matcher(trimmed).matches());
    }

    private boolean isListLine(String line) {
        return UNORDERED_LIST_PATTERN.matcher(line).matches() || ORDERED_LIST_PATTERN.matcher(line).matches();
    }

    private boolean isListContinuation(String line) {
        return line.startsWith(" ") || line.startsWith("\t");
    }

    private void updateSectionStack(String[] sectionStack, String heading) {
        Matcher matcher = HEADING_PATTERN.matcher(heading.trim());
        if (!matcher.matches()) {
            return;
        }
        int level = matcher.group(1).length();
        sectionStack[level - 1] = matcher.group(2).trim();
        Arrays.fill(sectionStack, level, sectionStack.length, null);
    }

    private String currentSectionPath(String[] sectionStack) {
        List<String> path = Arrays.stream(sectionStack)
                .filter(StringUtils::hasText)
                .toList();
        return String.join(" > ", path);
    }

    private record LinePart(String text, int charStart, int charEnd) {
    }
}
