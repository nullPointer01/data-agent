package com.ai.agent.agentic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 专家协作实现开关；只改变 ORCHESTRATED 的执行机制，不改变授权和请求规划。 */
@Component
@ConfigurationProperties(prefix = "app.agent.orchestration")
public class AgentOrchestrationProperties {
    public enum Engine { TOOL_CALLING, SUPERVISOR }

    private Engine engine = Engine.TOOL_CALLING;

    public Engine getEngine() { return engine; }

    public void setEngine(Engine engine) {
        this.engine = java.util.Objects.requireNonNull(engine, "编排引擎不能为空");
    }
}
