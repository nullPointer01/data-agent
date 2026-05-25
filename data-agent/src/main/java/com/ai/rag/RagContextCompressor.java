package com.ai.rag;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 上下文压缩器。
 *
 * @author data-agent
 */
@Component
public class RagContextCompressor {

    private static final String CONTEXT_TRUNCATED_MARKER = "\n[检索上下文已压缩]";
    private static final String SENTENCE_BOUNDARY_REGEX = "(?<=[。！？.!?；;])";
    private static final int MIN_SNIPPET_CHARS = 80;

    /**
     * 在保留引用头的前提下压缩上下文片段，避免直接把某段资料截断到不可读。
     *
     * @param sections 待压缩的上下文片段
     * @param maxChars 最大字符数
     * @return 压缩后的上下文
     */
    public String compress(List<RagContextSection> sections, int maxChars) {
        if (sections == null || sections.isEmpty() || maxChars <= 0) {
            return "";
        }
        StringBuilder contextBuilder = new StringBuilder();
        for (RagContextSection section : sections) {
            if (section == null || !StringUtils.hasText(section.header())) {
                continue;
            }
            String rendered = render(section, maxChars - contextBuilder.length());
            if (!StringUtils.hasText(rendered)) {
                break;
            }
            if (!contextBuilder.isEmpty()) {
                contextBuilder.append("\n\n");
            }
            contextBuilder.append(rendered);
            if (contextBuilder.length() >= maxChars) {
                break;
            }
        }
        return contextBuilder.toString().trim();
    }

    private String render(RagContextSection section, int remainingChars) {
        if (remainingChars <= 0) {
            return "";
        }
        String header = section.header().trim();
        String content = section.content() == null ? "" : section.content().trim();
        int contentBudget = remainingChars - header.length() - 1;
        if (contentBudget <= 0) {
            return "";
        }
        return header + "\n" + compressContent(content, contentBudget);
    }

    private String compressContent(String content, int contentBudget) {
        if (content.length() <= contentBudget) {
            return content;
        }
        int markerBudget = Math.max(0, contentBudget - CONTEXT_TRUNCATED_MARKER.length());
        if (markerBudget <= 0) {
            return "";
        }
        String snippet = bestEffortSnippet(content, markerBudget);
        return snippet + CONTEXT_TRUNCATED_MARKER;
    }

    private String bestEffortSnippet(String content, int maxChars) {
        if (content.length() <= maxChars) {
            return content;
        }
        if (maxChars < MIN_SNIPPET_CHARS) {
            return content.substring(0, maxChars).trim();
        }
        StringBuilder snippet = new StringBuilder();
        for (String sentence : splitSentences(content)) {
            if (!StringUtils.hasText(sentence)) {
                continue;
            }
            if (snippet.length() + sentence.length() > maxChars) {
                break;
            }
            snippet.append(sentence.trim());
        }
        if (!snippet.isEmpty()) {
            return snippet.toString();
        }
        return content.substring(0, maxChars).trim();
    }

    private List<String> splitSentences(String content) {
        List<String> sentences = new ArrayList<>();
        for (String item : content.split(SENTENCE_BOUNDARY_REGEX)) {
            if (StringUtils.hasText(item)) {
                sentences.add(item);
            }
        }
        return sentences;
    }
}
