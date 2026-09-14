package com.ai.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 增强推理配置。
 *
 * @author data-agent
 */
@ConfigurationProperties(prefix = "app.agent.reasoning")
public class AgentReasoningProperties {

    private boolean reflectionEnabled = true;

    private boolean workingMemoryEnabled = true;

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
