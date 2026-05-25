package com.ai.rag;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将模型提示词改写成适合检索的查询。
 *
 * @author data-agent
 */
@Component
public class RagQueryRewriter {

    private static final int MAX_QUERY_LENGTH = 300;
    private static final int MAX_KEYWORDS = 8;
    private static final String USER_QUESTION_MARKER = "[用户问题]";
    private static final String RETRIEVAL_CONTEXT_MARKER = "[检索上下文]";
    private static final String MEMORY_CONTEXT_MARKER = "[记忆上下文]";
    private static final String FILE_HINT = "[用户已上传文件数据";
    private static final List<String> STOP_WORDS = List.of("请问", "请", "帮我", "一下", "这个", "那个", "什么",
            "如何", "怎么", "为什么", "是否", "进行", "一个", "以及", "或者", "如果");

    /**
     * 将原始输入改写成检索查询。
     *
     * @param rawQuery 原始查询或增强提示词
     * @return 检索查询
     */
    public String rewrite(String rawQuery) {
        return analyze(rawQuery).rewrittenQuery();
    }

    /**
     * 分析查询并生成关键词和检索变体。
     *
     * @param rawQuery 原始查询
     * @return 查询分析结果
     */
    public RagQueryAnalysis analyze(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return new RagQueryAnalysis("", "", List.of(), "GENERAL", List.of());
        }
        String query = normalize(rawQuery);
        query = extractAfterMarker(query, USER_QUESTION_MARKER);
        query = removeSectionBefore(query, RETRIEVAL_CONTEXT_MARKER);
        query = removeSectionBefore(query, MEMORY_CONTEXT_MARKER);
        query = removeFileHint(query);
        String rewrittenQuery = truncate(query.trim());
        List<String> keywords = extractKeywords(rewrittenQuery);
        return new RagQueryAnalysis(rawQuery, rewrittenQuery, keywords, classifyQuery(rewrittenQuery),
                buildVariants(rewrittenQuery, keywords));
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    private String extractAfterMarker(String query, String marker) {
        int index = query.lastIndexOf(marker);
        if (index < 0) {
            return query;
        }
        return query.substring(index + marker.length()).trim();
    }

    private String removeSectionBefore(String query, String marker) {
        int index = query.indexOf(marker);
        if (index < 0) {
            return query;
        }
        int nextSection = query.indexOf("[", index + marker.length());
        if (nextSection < 0) {
            return query.substring(0, index).trim();
        }
        return (query.substring(0, index) + query.substring(nextSection)).trim();
    }

    private String removeFileHint(String query) {
        int index = query.indexOf(FILE_HINT);
        if (index < 0) {
            return query;
        }
        return query.substring(0, index).trim();
    }

    private String truncate(String query) {
        if (query.length() <= MAX_QUERY_LENGTH) {
            return query;
        }
        return query.substring(0, MAX_QUERY_LENGTH);
    }

    private List<String> extractKeywords(String query) {
        Set<String> keywords = new LinkedHashSet<>();
        String normalized = query.replaceAll("[，。！？；：,.!?;:()（）\\[\\]【】]", " ");
        for (String token : normalized.split("\\s+")) {
            String candidate = token.trim();
            if (isUsefulKeyword(candidate)) {
                keywords.add(candidate);
            }
            if (keywords.size() >= MAX_KEYWORDS) {
                break;
            }
        }
        collectChineseDomainKeywords(query, keywords);
        return keywords.stream().limit(MAX_KEYWORDS).toList();
    }

    private boolean isUsefulKeyword(String candidate) {
        return candidate.length() >= 2 && !STOP_WORDS.contains(candidate);
    }

    private void collectChineseDomainKeywords(String query, Set<String> keywords) {
        List<String> domainKeywords = List.of("销售", "订单", "库存", "客户", "用户", "流失", "售后", "利润",
                "成本", "同比", "环比", "报表", "合同", "制度", "流程", "数据源", "知识库");
        for (String keyword : domainKeywords) {
            if (query.contains(keyword)) {
                keywords.add(keyword);
            }
        }
    }

    private String classifyQuery(String query) {
        if (query.contains("对比") || query.contains("同比") || query.contains("环比")) {
            return "COMPARISON";
        }
        if (query.contains("原因") || query.contains("为什么") || query.contains("分析")) {
            return "ANALYSIS";
        }
        if (query.contains("步骤") || query.contains("流程") || query.contains("怎么")) {
            return "PROCEDURE";
        }
        return "FACT";
    }

    private List<String> buildVariants(String rewrittenQuery, List<String> keywords) {
        List<String> variants = new ArrayList<>();
        if (StringUtils.hasText(rewrittenQuery)) {
            variants.add(rewrittenQuery);
        }
        if (!keywords.isEmpty()) {
            variants.add(String.join(" ", keywords));
        }
        return variants.stream().distinct().toList();
    }
}
