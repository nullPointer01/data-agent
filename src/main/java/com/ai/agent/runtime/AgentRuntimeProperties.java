package com.ai.agent.runtime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Agent Harness 的服务端运行限制。
 *
 * @author data-agent
 */
@Validated
@ConfigurationProperties(prefix = "app.agent.runtime")
public class AgentRuntimeProperties {

    @NotNull
    private Duration timeout = Duration.ofMinutes(5);

    @Positive
    private int maxIterations = 8;

    @Positive
    private int maxModelCalls = 24;

    @Positive
    private int maxToolCalls = 32;

    @Positive
    private long maxTokens = 100_000L;

    /**
     * 生成单次运行使用的不可变限制快照。
     *
     * @return 运行限制
     */
    public AgentRunLimits toLimits() {
        return new AgentRunLimits(timeout, maxIterations, maxModelCalls, maxToolCalls, maxTokens);
    }

    /**
     * 校验运行超时必须为正数。
     *
     * @return 是否为有效超时
     */
    @AssertTrue(message = "app.agent.runtime.timeout 必须为正数")
    public boolean isTimeoutValid() {
        return timeout != null && !timeout.isZero() && !timeout.isNegative();
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public int getMaxModelCalls() {
        return maxModelCalls;
    }

    public void setMaxModelCalls(int maxModelCalls) {
        this.maxModelCalls = maxModelCalls;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public long getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(long maxTokens) {
        this.maxTokens = maxTokens;
    }
}
