package com.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 外部 Cross-Encoder 精排配置。
 *
 * @author data-agent
 */
@ConfigurationProperties(prefix = "app.rag.reranker")
public class RerankerProperties {

    private boolean enabled = true;
    private String baseUrl = "https://api.siliconflow.cn/v1/rerank";
    private String apiKey = "";
    private String modelName = "BAAI/bge-reranker-v2-m3";
    private Duration timeout = Duration.ofSeconds(5);
    private int maxCandidates = 20;
    private int maxDocumentChars = 4000;
    private boolean failOpen = true;
    private int maxAttempts = 2;
    private Duration initialBackoff = Duration.ofMillis(200);
    private int circuitFailureThreshold = 5;
    private Duration circuitOpenDuration = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxCandidates() {
        return maxCandidates;
    }

    public void setMaxCandidates(int maxCandidates) {
        this.maxCandidates = maxCandidates;
    }

    public int getMaxDocumentChars() {
        return maxDocumentChars;
    }

    public void setMaxDocumentChars(int maxDocumentChars) {
        this.maxDocumentChars = maxDocumentChars;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = initialBackoff;
    }

    public int getCircuitFailureThreshold() {
        return circuitFailureThreshold;
    }

    public void setCircuitFailureThreshold(int circuitFailureThreshold) {
        this.circuitFailureThreshold = circuitFailureThreshold;
    }

    public Duration getCircuitOpenDuration() {
        return circuitOpenDuration;
    }

    public void setCircuitOpenDuration(Duration circuitOpenDuration) {
        this.circuitOpenDuration = circuitOpenDuration;
    }

    /**
     * 在创建 HTTP 客户端前校验运行参数。
     */
    public void validate() {
        requireText(baseUrl, "app.rag.reranker.base-url");
        requireText(modelName, "app.rag.reranker.model-name");
        requirePositiveDuration(timeout, "app.rag.reranker.timeout");
        requireRange(maxCandidates, 1, 100, "app.rag.reranker.max-candidates");
        requireRange(maxDocumentChars, 128, 20000, "app.rag.reranker.max-document-chars");
        requireRange(maxAttempts, 1, 5, "app.rag.reranker.max-attempts");
        requirePositiveDuration(initialBackoff, "app.rag.reranker.initial-backoff");
        requireRange(circuitFailureThreshold, 1, 100, "app.rag.reranker.circuit-failure-threshold");
        requirePositiveDuration(circuitOpenDuration, "app.rag.reranker.circuit-open-duration");
    }

    private void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Reranker 配置不能为空: " + propertyName);
        }
    }

    private void requireRange(int value, int minimum, int maximum, String propertyName) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Reranker 配置超出范围 [" + minimum + ", " + maximum + "]: "
                    + propertyName);
        }
    }

    private void requirePositiveDuration(Duration value, String propertyName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Reranker 配置必须为正时长: " + propertyName);
        }
    }
}
