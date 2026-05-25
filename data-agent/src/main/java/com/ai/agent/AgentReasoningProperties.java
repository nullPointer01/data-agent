package com.ai.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 增强推理配置。
 *
 * @author data-agent
 */
@ConfigurationProperties(prefix = "app.agent.reasoning")
public class AgentReasoningProperties {

    private boolean fastPathEnabled = true;

    private boolean planningEnabled = true;

    private boolean parallelPrecheckEnabled = true;

    private boolean reflectionEnabled = true;

    private boolean workingMemoryEnabled = true;

    public boolean isFastPathEnabled() {
        return fastPathEnabled;
    }

    public void setFastPathEnabled(boolean fastPathEnabled) {
        this.fastPathEnabled = fastPathEnabled;
    }

    public boolean isPlanningEnabled() {
        return planningEnabled;
    }

    public void setPlanningEnabled(boolean planningEnabled) {
        this.planningEnabled = planningEnabled;
    }

    public boolean isParallelPrecheckEnabled() {
        return parallelPrecheckEnabled;
    }

    public void setParallelPrecheckEnabled(boolean parallelPrecheckEnabled) {
        this.parallelPrecheckEnabled = parallelPrecheckEnabled;
    }

    public boolean isReflectionEnabled() {
        return reflectionEnabled;
    }

    public void setReflectionEnabled(boolean reflectionEnabled) {
        this.reflectionEnabled = reflectionEnabled;
    }

    public boolean isWorkingMemoryEnabled() {
        return workingMemoryEnabled;
    }

    public void setWorkingMemoryEnabled(boolean workingMemoryEnabled) {
        this.workingMemoryEnabled = workingMemoryEnabled;
    }
}
