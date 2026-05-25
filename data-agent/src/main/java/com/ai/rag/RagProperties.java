package com.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 检索配置。
 *
 * @author data-agent
 */
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private boolean enabled = true;

    private int topK = 6;

    private int candidateTopK = 20;

    private int maxContextChars = 5000;

    private double minScore = 0.45D;

    private String fullTextProvider = "jpa";

    private HealthProperties health = new HealthProperties();

    private QualityProperties quality = new QualityProperties();

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

    public HealthProperties getHealth() {
        return health;
    }

    public void setHealth(HealthProperties health) {
        this.health = health;
    }

    public QualityProperties getQuality() {
        return quality;
    }

    public void setQuality(QualityProperties quality) {
        this.quality = quality;
    }

    /**
     * RAG 健康和验收阈值配置。
     */
    public static class HealthProperties {

        private long esLatencyThresholdMs = 100L;

        private long milvusLatencyThresholdMs = 50L;

        private long ragLatencyThresholdMs = 2000L;

        public long getEsLatencyThresholdMs() {
            return esLatencyThresholdMs;
        }

        public void setEsLatencyThresholdMs(long esLatencyThresholdMs) {
            this.esLatencyThresholdMs = esLatencyThresholdMs;
        }

        public long getMilvusLatencyThresholdMs() {
            return milvusLatencyThresholdMs;
        }

        public void setMilvusLatencyThresholdMs(long milvusLatencyThresholdMs) {
            this.milvusLatencyThresholdMs = milvusLatencyThresholdMs;
        }

        public long getRagLatencyThresholdMs() {
            return ragLatencyThresholdMs;
        }

        public void setRagLatencyThresholdMs(long ragLatencyThresholdMs) {
            this.ragLatencyThresholdMs = ragLatencyThresholdMs;
        }
    }

    /**
     * RAG 质量评估阈值配置。
     */
    public static class QualityProperties {

        private double overallPassThreshold = 0.75D;

        private double relevanceThreshold = 0.60D;

        private double citationAccuracyThreshold = 0.80D;

        private double hybridCoverageThreshold = 0.50D;

        private double latencyScoreThreshold = 0.80D;

        private double sourceCoverageThreshold = 0.80D;

        public double getOverallPassThreshold() {
            return overallPassThreshold;
        }

        public void setOverallPassThreshold(double overallPassThreshold) {
            this.overallPassThreshold = overallPassThreshold;
        }

        public double getRelevanceThreshold() {
            return relevanceThreshold;
        }

        public void setRelevanceThreshold(double relevanceThreshold) {
            this.relevanceThreshold = relevanceThreshold;
        }

        public double getCitationAccuracyThreshold() {
            return citationAccuracyThreshold;
        }

        public void setCitationAccuracyThreshold(double citationAccuracyThreshold) {
            this.citationAccuracyThreshold = citationAccuracyThreshold;
        }

        public double getHybridCoverageThreshold() {
            return hybridCoverageThreshold;
        }

        public void setHybridCoverageThreshold(double hybridCoverageThreshold) {
            this.hybridCoverageThreshold = hybridCoverageThreshold;
        }

        public double getLatencyScoreThreshold() {
            return latencyScoreThreshold;
        }

        public void setLatencyScoreThreshold(double latencyScoreThreshold) {
            this.latencyScoreThreshold = latencyScoreThreshold;
        }

        public double getSourceCoverageThreshold() {
            return sourceCoverageThreshold;
        }

        public void setSourceCoverageThreshold(double sourceCoverageThreshold) {
            this.sourceCoverageThreshold = sourceCoverageThreshold;
        }
    }
}
