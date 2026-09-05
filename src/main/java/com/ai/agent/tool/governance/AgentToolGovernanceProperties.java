package com.ai.agent.tool.governance;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Agent 工具治理的服务端保护性上限。
 *
 * @author data-agent
 */
@Validated
@ConfigurationProperties(prefix = "app.agent.tool-governance")
public class AgentToolGovernanceProperties {

    @Min(1)
    private int maxArgumentLength = 16_384;

    @Min(1)
    private int maxResultLength = 20_000;

    @NotNull
    private Duration defaultTimeout = Duration.ofSeconds(20);

    @Min(1)
    private int maxAttempts = 2;

    @NotNull
    private Duration initialBackoff = Duration.ofMillis(100);

    @NotNull
    private Duration maxBackoff = Duration.ofSeconds(1);

    @Min(1)
    private int journalCapacity = 64;

    @NotNull
    private AgentToolRiskLevel maxRisk = AgentToolRiskLevel.HIGH;

    private Set<String> enabledTools = new LinkedHashSet<>();

    @Min(1)
    private int corePoolSize = 2;

    @Min(1)
    private int maxPoolSize = 8;

    @Min(1)
    private int queueCapacity = 100;

    @AssertTrue(message = "tool-governance duration 和线程池配置必须为正数且边界有效")
    public boolean isValid() {
        return isPositive(defaultTimeout)
                && isPositive(initialBackoff)
                && isPositive(maxBackoff)
                && initialBackoff.compareTo(maxBackoff) <= 0
                && corePoolSize <= maxPoolSize;
    }

    private boolean isPositive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }

    public int getMaxArgumentLength() {
        return maxArgumentLength;
    }

    public void setMaxArgumentLength(int maxArgumentLength) {
        this.maxArgumentLength = maxArgumentLength;
    }

    public int getMaxResultLength() {
        return maxResultLength;
    }

    public void setMaxResultLength(int maxResultLength) {
        this.maxResultLength = maxResultLength;
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
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

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
        this.maxBackoff = maxBackoff;
    }

    public int getJournalCapacity() {
        return journalCapacity;
    }

    public void setJournalCapacity(int journalCapacity) {
        this.journalCapacity = journalCapacity;
    }

    public AgentToolRiskLevel getMaxRisk() {
        return maxRisk;
    }

    public void setMaxRisk(AgentToolRiskLevel maxRisk) {
        this.maxRisk = maxRisk;
    }

    public Set<String> getEnabledTools() {
        return Set.copyOf(enabledTools);
    }

    public void setEnabledTools(Set<String> enabledTools) {
        this.enabledTools = enabledTools == null ? new LinkedHashSet<>() : new LinkedHashSet<>(enabledTools);
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
}
