package com.ai.agent.durable;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 持久化 Agent Run、人工审批和恢复租约的服务端配置。
 *
 * @author data-agent
 */
@Validated
@ConfigurationProperties(prefix = "app.agent.durable")
public class AgentDurableRuntimeProperties {

    private boolean enabled;

    private boolean sandboxToolEnabled;

    @NotNull
    private Duration approvalTtl = Duration.ofHours(24);

    @NotNull
    private Duration leaseDuration = Duration.ofSeconds(60);

    @NotNull
    private Duration scanInterval = Duration.ofSeconds(15);

    @NotNull
    private Duration terminalRetention = Duration.ofDays(30);

    @Positive
    private int scanBatchSize = 50;

    @Positive
    private int maxResumeAttempts = 3;

    private Set<String> enabledApprovalTools = new LinkedHashSet<>();

    @AssertTrue(message = "app.agent.durable 的时长配置必须全部为正数")
    public boolean isDurationConfigurationValid() {
        return isPositive(approvalTtl)
                && isPositive(leaseDuration)
                && isPositive(scanInterval)
                && isPositive(terminalRetention);
    }

    @AssertTrue(message = "app.agent.durable.lease-duration 必须大于 scan-interval")
    public boolean isLeaseTimingValid() {
        return leaseDuration == null
                || scanInterval == null
                || leaseDuration.compareTo(scanInterval) > 0;
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSandboxToolEnabled() {
        return sandboxToolEnabled;
    }

    public void setSandboxToolEnabled(boolean sandboxToolEnabled) {
        this.sandboxToolEnabled = sandboxToolEnabled;
    }

    public Duration getApprovalTtl() {
        return approvalTtl;
    }

    public void setApprovalTtl(Duration approvalTtl) {
        this.approvalTtl = approvalTtl;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getScanInterval() {
        return scanInterval;
    }

    public void setScanInterval(Duration scanInterval) {
        this.scanInterval = scanInterval;
    }

    public Duration getTerminalRetention() {
        return terminalRetention;
    }

    public void setTerminalRetention(Duration terminalRetention) {
        this.terminalRetention = terminalRetention;
    }

    public int getScanBatchSize() {
        return scanBatchSize;
    }

    public void setScanBatchSize(int scanBatchSize) {
        this.scanBatchSize = scanBatchSize;
    }

    public int getMaxResumeAttempts() {
        return maxResumeAttempts;
    }

    public void setMaxResumeAttempts(int maxResumeAttempts) {
        this.maxResumeAttempts = maxResumeAttempts;
    }

    public Set<String> getEnabledApprovalTools() {
        return Set.copyOf(enabledApprovalTools);
    }

    public void setEnabledApprovalTools(Set<String> enabledApprovalTools) {
        this.enabledApprovalTools = enabledApprovalTools == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(enabledApprovalTools);
    }
}
