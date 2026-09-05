package com.ai.rag.dto;

import java.util.List;

/**
 * Agent 执行前召回的 RAG 上下文。
 *
 * @author data-agent
 */
public class RagContextResponse {

    private String context;

    private int hitCount;

    private String rewrittenQuery = "";

    private String queryType = "GENERAL";

    private List<String> keywords = List.of();

    private List<RagCitation> citations = List.of();

    private RagRetrievalTrace trace = RagRetrievalTrace.empty();

    public RagContextResponse() {
    }

    public RagContextResponse(String context, int hitCount) {
        this(context, hitCount, List.of());
    }

    public RagContextResponse(String context, int hitCount, List<RagCitation> citations) {
        this(context, hitCount, "", "GENERAL", List.of(), citations);
    }

    public RagContextResponse(String context, int hitCount, String rewrittenQuery, String queryType,
            List<String> keywords, List<RagCitation> citations) {
        this(context, hitCount, rewrittenQuery, queryType, keywords, citations, RagRetrievalTrace.empty());
    }

    public RagContextResponse(String context, int hitCount, String rewrittenQuery, String queryType,
            List<String> keywords, List<RagCitation> citations, RagRetrievalTrace trace) {
        this.context = context;
        this.hitCount = hitCount;
        this.rewrittenQuery = rewrittenQuery == null ? "" : rewrittenQuery;
        this.queryType = queryType == null ? "GENERAL" : queryType;
        this.keywords = keywords == null ? List.of() : List.copyOf(keywords);
        this.citations = citations == null ? List.of() : List.copyOf(citations);
        this.trace = trace == null ? RagRetrievalTrace.empty() : trace;
    }

    /**
     * 返回空 RAG 上下文。
     *
     * @return 空响应
     */
    public static RagContextResponse empty() {
        return new RagContextResponse("", 0, List.of());
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public int getHitCount() {
        return hitCount;
    }

    public void setHitCount(int hitCount) {
        this.hitCount = hitCount;
    }

    public String getRewrittenQuery() {
        return rewrittenQuery;
    }

    public void setRewrittenQuery(String rewrittenQuery) {
        this.rewrittenQuery = rewrittenQuery == null ? "" : rewrittenQuery;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType == null ? "GENERAL" : queryType;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    public List<RagCitation> getCitations() {
        return citations;
    }

    public void setCitations(List<RagCitation> citations) {
        this.citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public RagRetrievalTrace getTrace() {
        return trace;
    }

    public void setTrace(RagRetrievalTrace trace) {
        this.trace = trace == null ? RagRetrievalTrace.empty() : trace;
    }

    public boolean hasContext() {
        return context != null && !context.isBlank();
    }
}
