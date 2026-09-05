package com.ai.vector;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Embedding 模型配置。
 *
 * @author data-agent
 */
@Component
@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {

    private int dimension;

    private String indexVersion = "v1";

    private boolean normalize = true;

    private String metric = "COSINE";

    private Api api = new Api();

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public String getIndexVersion() {
        return indexVersion;
    }

    public void setIndexVersion(String indexVersion) {
        this.indexVersion = indexVersion;
    }

    public boolean isNormalize() {
        return normalize;
    }

    public void setNormalize(boolean normalize) {
        this.normalize = normalize;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public static class Api {

        private String baseUrl;

        private String apiKey = "";

        private String modelName;

        private Duration timeout;

        private Integer outputDimensions;

        private int batchSize = 32;

        private int maxAttempts = 3;

        private Duration initialBackoff = Duration.ofMillis(200);

        private int circuitFailureThreshold = 5;

        private Duration circuitOpenDuration = Duration.ofSeconds(30);

        private String queryPrefix = "";

        private String documentPrefix = "";

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

        public Integer getOutputDimensions() {
            return outputDimensions;
        }

        public void setOutputDimensions(Integer outputDimensions) {
            this.outputDimensions = outputDimensions;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
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

        public String getQueryPrefix() {
            return queryPrefix;
        }

        public void setQueryPrefix(String queryPrefix) {
            this.queryPrefix = queryPrefix;
        }

        public String getDocumentPrefix() {
            return documentPrefix;
        }

        public void setDocumentPrefix(String documentPrefix) {
            this.documentPrefix = documentPrefix;
        }

        /**
         * 校验外部 Embedding 调用策略，避免非法配置进入首次网络请求。
         *
         * @param expectedDimension 当前 Profile 的期望维度
         */
        public void validateRuntime(int expectedDimension) {
            requirePositiveDuration(timeout, "app.embedding.api.timeout");
            requireRange(batchSize, 1, 128, "app.embedding.api.batch-size");
            requireRange(maxAttempts, 1, 5, "app.embedding.api.max-attempts");
            requirePositiveDuration(initialBackoff, "app.embedding.api.initial-backoff");
            requirePositive(circuitFailureThreshold, "app.embedding.api.circuit-failure-threshold");
            requirePositiveDuration(circuitOpenDuration, "app.embedding.api.circuit-open-duration");
            if (outputDimensions != null) {
                requirePositive(outputDimensions, "app.embedding.api.output-dimensions");
                if (outputDimensions != expectedDimension) {
                    throw new IllegalArgumentException("Embedding 配置必须等于 app.embedding.dimension: "
                            + "app.embedding.api.output-dimensions");
                }
            }
        }

        private void requireRange(int value, int minimum, int maximum, String propertyName) {
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException("Embedding 配置超出范围 [" + minimum + ", " + maximum
                        + "]: " + propertyName);
            }
        }

        private void requirePositive(int value, String propertyName) {
            if (value <= 0) {
                throw new IllegalArgumentException("Embedding 配置必须为正整数: " + propertyName);
            }
        }

        private void requirePositiveDuration(Duration value, String propertyName) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("Embedding 配置必须为正时长: " + propertyName);
            }
        }
    }
}
