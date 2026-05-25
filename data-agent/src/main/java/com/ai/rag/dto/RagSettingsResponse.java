package com.ai.rag.dto;

/**
 * 暴露给管理端的 RAG 配置。
 *
 * @author data-agent
 */
public class RagSettingsResponse {

    private boolean enabled;

    private int topK;

    private int candidateTopK;

    private int maxContextChars;

    private double minScore;

    private String fullTextProvider;

    private String vectorProvider;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getCandidateTopK() {
        return candidateTopK;
    }

    public void setCandidateTopK(int candidateTopK) {
        this.candidateTopK = candidateTopK;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public String getFullTextProvider() {
        return fullTextProvider;
    }

    public void setFullTextProvider(String fullTextProvider) {
        this.fullTextProvider = fullTextProvider;
    }

    public String getVectorProvider() {
        return vectorProvider;
    }

    public void setVectorProvider(String vectorProvider) {
        this.vectorProvider = vectorProvider;
    }
}
