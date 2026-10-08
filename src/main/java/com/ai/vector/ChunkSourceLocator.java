package com.ai.vector;

import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 框架不返回原文偏移：仅对齐已经切好的结果，不决定切分边界或重叠内容。
 */
final class ChunkSourceLocator {
    private ChunkSourceLocator() {
    }

    /**
     * 忽略框架改写的空白，对齐全部非空白内容；无法完整对应时拒绝伪造引用位置。
     */
    static List<Range> locate(String source, List<TextSegment> segments, int overlap) {
        StringBuilder compact = new StringBuilder();
        int[] offsets = new int[source.length()];
        for (int index = 0; index < source.length(); index++) {
            if (!space(source.charAt(index))) {
                offsets[compact.length()] = index;
                compact.append(source.charAt(index));
            }
        }
        List<String> texts = segments.stream().map(segment -> compact(segment.text())).toList();
        int[] starts = align(compact.toString(), texts, overlap);
        List<Range> ranges = new ArrayList<>();
        for (int index = 0; index < texts.size(); index++) {
            int start = starts[index];
            int end = start + texts.get(index).length();
            ranges.add(new Range(offsets[start], offsets[end - 1] + 1));
        }
        return ranges;
    }

    private static int[] align(String source, List<String> parts, int overlap) {
        int count = parts.size();
        int[] starts = new int[count];
        int[] candidates = new int[count];
        int[] coveredEnds = new int[count + 1];
        long[] remaining = new long[count + 1];
        for (int i = count - 1; i >= 0; i--) {
            if (parts.get(i).isEmpty()) throw invalid();
            remaining[i] = remaining[i + 1] + parts.get(i).length();
        }
        // 优先无重叠匹配；重复原文有多个合法位置时，回溯至能覆盖全文的顺序匹配。
        // 失败状态记忆避免重复探索；迭代实现避免超长文档造成递归栈溢出。
        Set<Long> failed = new HashSet<>();
        int index = 0;
        if (count > 0) candidates[0] = 0;
        while (index >= 0) {
            int previousEnd = coveredEnds[index];
            if (index == count) {
                if (previousEnd == source.length()) return starts;
                index--;
                continue;
            }
            long state = ((long) index << 32) | previousEnd;
            int minimum = Math.max(0, previousEnd - overlap);
            int start = failed.contains(state) || remaining[index] < source.length() - previousEnd ? -1
                    : source.lastIndexOf(parts.get(index), candidates[index]);
            if (start < minimum) {
                failed.add(state);
                index--;
                continue;
            }
            starts[index] = start;
            candidates[index] = start - 1;
            // 框架递归会重发仅含重叠的短片段；覆盖终点不能跟着短片段退回。
            coveredEnds[index + 1] = Math.max(previousEnd, start + parts.get(index).length());
            index++;
            if (index < count) candidates[index] = coveredEnds[index];
        }
        throw invalid();
    }

    private static String compact(String text) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length(); index++)
            if (!space(text.charAt(index))) result.append(text.charAt(index));
        return result.toString();
    }

    private static boolean space(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("框架分块无法完整对应原文，停止索引以避免错误引用");
    }

    record Range(int start, int end) {
    }
}
