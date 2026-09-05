package com.ai.agent.orchestrator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Orchestrator 编排配置。
 *
 * @author data-agent
 */
@ConfigurationProperties(prefix = "app.orchestrator")
public class OrchestratorProperties {

    private boolean llmIntentEnabled;

    private boolean llmPlanningEnabled;

    private String modelId;

    private double intentConfidenceThreshold = 0.8D;

    private int maxPlanTasks = 6;

    public boolean isLlmIntentEnabled() {
        return llmIntentEnabled;
    }

    public void setLlmIntentEnabled(boolean llmIntentEnabled) {
        this.llmIntentEnabled = llmIntentEnabled;
    }

    public boolean isLlmPlanningEnabled() {
        return llmPlanningEnabled;
    }

    public void setLlmPlanningEnabled(boolean llmPlanningEnabled) {
        this.llmPlanningEnabled = llmPlanningEnabled;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public double getIntentConfidenceThreshold() {
        return intentConfidenceThreshold;
    }

    public void setIntentConfidenceThreshold(double intentConfidenceThreshold) {
        this.intentConfidenceThreshold = intentConfidenceThreshold;
    }

    public int getMaxPlanTasks() {
        return maxPlanTasks;
    }

    public void setMaxPlanTasks(int maxPlanTasks) {
        this.maxPlanTasks = maxPlanTasks;
    }
}
