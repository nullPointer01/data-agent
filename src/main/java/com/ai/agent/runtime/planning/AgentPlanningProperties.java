package com.ai.agent.runtime.planning;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Agent 请求规划与语义 Router 的固定运行参数。
 *
 * @author data-agent
 */
@Validated
@ConfigurationProperties(prefix = "app.agent.planning")
public class AgentPlanningProperties {

    private boolean semanticRouterEnabled = true;
    private String modelId = "";

    /** 显式兼容不支持原生 Schema 的端点，避免失败后重复调用增加延迟和费用。 */
    @NotNull
    private OutputFormat responseFormat = OutputFormat.JSON_SCHEMA;

    /** 规划响应协议；两种协议都必须经过相同的本地合同和授权校验。 */
    public enum OutputFormat {
        JSON_SCHEMA,
        JSON_OBJECT
    }

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double minimumConfidence = 0.75D;

    @Min(0)
    @Max(8000)
    private int maxHistoryChars = 1500;

    @Min(50)
    @Max(2000)
    private int maxQueryChars = 500;

    public boolean isSemanticRouterEnabled() {
        return semanticRouterEnabled;
    }

    public void setSemanticRouterEnabled(boolean semanticRouterEnabled) {
        this.semanticRouterEnabled = semanticRouterEnabled;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId == null ? "" : modelId.trim();
    }

    public OutputFormat getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(OutputFormat responseFormat) {
        this.responseFormat = responseFormat;
    }

    public double getMinimumConfidence() {
        return minimumConfidence;
    }

    public void setMinimumConfidence(double minimumConfidence) {
        this.minimumConfidence = minimumConfidence;
    }

    public int getMaxHistoryChars() {
        return maxHistoryChars;
    }

    public void setMaxHistoryChars(int maxHistoryChars) {
        this.maxHistoryChars = maxHistoryChars;
    }

    public int getMaxQueryChars() {
        return maxQueryChars;
    }

    public void setMaxQueryChars(int maxQueryChars) {
        this.maxQueryChars = maxQueryChars;
    }
}
