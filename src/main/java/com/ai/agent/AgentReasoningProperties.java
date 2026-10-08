package com.ai.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 增强推理配置。
 *
 * @author data-agent
 */
@ConfigurationProperties(prefix = "app.agent.reasoning")
public class AgentReasoningProperties {

    private boolean workingMemoryEnabled = true;

    public boolean isWorkingMemoryEnabled() {
        return workingMemoryEnabled;
    }

    public void setWorkingMemoryEnabled(boolean workingMemoryEnabled) {
        this.workingMemoryEnabled = workingMemoryEnabled;
    }
}
