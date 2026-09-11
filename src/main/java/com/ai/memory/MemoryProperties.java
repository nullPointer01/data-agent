package com.ai.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆体系配置。
 *
 * @author data-agent
 */
@ConfigurationProperties(prefix = "app.memory")
public class MemoryProperties {

    private boolean modelCompressionEnabled = false;

    private boolean semanticExtractionEnabled = true;

    private double semanticExtractionMinConfidence = 0.85D;

    private int retentionBatchSize = 200;

    private int shortTermRetentionDays = 30;

    private int maxMemoriesPerUser = 500;

    private int promotionAccessCountThreshold = 3;

    private double promotionDecayWeightThreshold = 0.8D;

    private double shortTermDeleteThreshold = 0.1D;

    private int longTermDecayAfterDays = 90;

    private double shortTermDecayRate = 0.95D;

    private double longTermDecayRate = 0.99D;

    public boolean isModelCompressionEnabled() {
        return modelCompressionEnabled;
    }

    public void setModelCompressionEnabled(boolean modelCompressionEnabled) {
        this.modelCompressionEnabled = modelCompressionEnabled;
    }

    public boolean isSemanticExtractionEnabled() {
        return semanticExtractionEnabled;
    }

    public void setSemanticExtractionEnabled(boolean semanticExtractionEnabled) {
        this.semanticExtractionEnabled = semanticExtractionEnabled;
    }

    public double getSemanticExtractionMinConfidence() {
        return semanticExtractionMinConfidence;
    }

    public void setSemanticExtractionMinConfidence(double semanticExtractionMinConfidence) {
        this.semanticExtractionMinConfidence = semanticExtractionMinConfidence;
    }

    public int getRetentionBatchSize() {
        return retentionBatchSize;
    }

    public void setRetentionBatchSize(int retentionBatchSize) {
        this.retentionBatchSize = retentionBatchSize;
    }

    public int getShortTermRetentionDays() {
        return shortTermRetentionDays;
    }

    public void setShortTermRetentionDays(int shortTermRetentionDays) {
        this.shortTermRetentionDays = shortTermRetentionDays;
    }

    public int getMaxMemoriesPerUser() {
        return maxMemoriesPerUser;
    }

    public void setMaxMemoriesPerUser(int maxMemoriesPerUser) {
        this.maxMemoriesPerUser = maxMemoriesPerUser;
    }

    public int getPromotionAccessCountThreshold() {
        return promotionAccessCountThreshold;
    }

    public void setPromotionAccessCountThreshold(int promotionAccessCountThreshold) {
        this.promotionAccessCountThreshold = promotionAccessCountThreshold;
    }

    public double getPromotionDecayWeightThreshold() {
        return promotionDecayWeightThreshold;
    }

    public void setPromotionDecayWeightThreshold(double promotionDecayWeightThreshold) {
        this.promotionDecayWeightThreshold = promotionDecayWeightThreshold;
    }

    public double getShortTermDeleteThreshold() {
        return shortTermDeleteThreshold;
    }

    public void setShortTermDeleteThreshold(double shortTermDeleteThreshold) {
        this.shortTermDeleteThreshold = shortTermDeleteThreshold;
    }

    public int getLongTermDecayAfterDays() {
        return longTermDecayAfterDays;
    }

    public void setLongTermDecayAfterDays(int longTermDecayAfterDays) {
        this.longTermDecayAfterDays = longTermDecayAfterDays;
    }

    public double getShortTermDecayRate() {
        return shortTermDecayRate;
    }

    public void setShortTermDecayRate(double shortTermDecayRate) {
        this.shortTermDecayRate = shortTermDecayRate;
    }

    public double getLongTermDecayRate() {
        return longTermDecayRate;
    }

    public void setLongTermDecayRate(double longTermDecayRate) {
        this.longTermDecayRate = longTermDecayRate;
    }
}
