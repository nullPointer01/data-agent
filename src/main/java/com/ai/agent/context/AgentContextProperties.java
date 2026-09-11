package com.ai.agent.context;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 模型调用的统一上下文预算配置。
 *
 * @author data-agent
 */
@Validated
@ConfigurationProperties(prefix = "app.agent.context")
public class AgentContextProperties {

    @Positive
    private int defaultModelWindowTokens = 32_768;

    @Positive
    private int reservedOutputTokens = 4_096;

    @Min(0)
    private int safetyMarginTokens = 1_024;

    @Min(1)
    private int protectedRecentMessages = 4;

    private Map<String, Integer> modelWindowOverrides = new LinkedHashMap<>();

    private final Compaction compaction = new Compaction();

    /**
     * 根据项目模型 ID 解析上下文窗口，未配置时使用保守默认值。
     *
     * @param modelId 项目内模型 ID
     * @return 模型上下文窗口 Token 数
     */
    public int resolveModelWindowTokens(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return defaultModelWindowTokens;
        }
        return modelWindowOverrides.getOrDefault(modelId.trim(), defaultModelWindowTokens);
    }

    /**
     * 校验默认模型窗口在扣除输出预留和安全边界后仍可容纳输入。
     *
     * @return 配置是否有效
     */
    @AssertTrue(message = "app.agent.context 默认模型窗口必须大于输出预留与安全边界之和")
    public boolean isDefaultWindowValid() {
        return hasInputCapacity(defaultModelWindowTokens);
    }

    /**
     * 校验每个模型覆盖窗口均为正数且保留有效输入空间。
     *
     * @return 配置是否有效
     */
    @AssertTrue(message = "app.agent.context.model-window-overrides 包含无效窗口")
    public boolean isModelWindowOverridesValid() {
        if (modelWindowOverrides == null) {
            return false;
        }
        return modelWindowOverrides.entrySet().stream()
                .allMatch(entry -> entry.getKey() != null
                        && !entry.getKey().isBlank()
                        && entry.getValue() != null
                        && hasInputCapacity(entry.getValue()));
    }

    /**
     * 第一版不开放未经评测的模型摘要，避免启用配置后发生隐式二次模型调用。
     *
     * @return 模型摘要是否保持关闭
     */
    @AssertTrue(message = "app.agent.context.compaction.model-summary-enabled 当前必须为 false")
    public boolean isModelSummarySwitchValid() {
        return !compaction.isModelSummaryEnabled();
    }

    private boolean hasInputCapacity(int windowTokens) {
        return windowTokens > 0
                && (long) reservedOutputTokens + safetyMarginTokens < windowTokens;
    }

    public int getDefaultModelWindowTokens() {
        return defaultModelWindowTokens;
    }

    public void setDefaultModelWindowTokens(int defaultModelWindowTokens) {
        this.defaultModelWindowTokens = defaultModelWindowTokens;
    }

    public int getReservedOutputTokens() {
        return reservedOutputTokens;
    }

    public void setReservedOutputTokens(int reservedOutputTokens) {
        this.reservedOutputTokens = reservedOutputTokens;
    }

    public int getSafetyMarginTokens() {
        return safetyMarginTokens;
    }

    public void setSafetyMarginTokens(int safetyMarginTokens) {
        this.safetyMarginTokens = safetyMarginTokens;
    }

    public int getProtectedRecentMessages() {
        return protectedRecentMessages;
    }

    public void setProtectedRecentMessages(int protectedRecentMessages) {
        this.protectedRecentMessages = protectedRecentMessages;
    }

    public Map<String, Integer> getModelWindowOverrides() {
        return modelWindowOverrides;
    }

    public void setModelWindowOverrides(Map<String, Integer> modelWindowOverrides) {
        this.modelWindowOverrides = modelWindowOverrides == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(modelWindowOverrides);
    }

    public Compaction getCompaction() {
        return compaction;
    }

    /**
     * 上下文压缩开关。第一版只允许显式开启，模型摘要默认关闭。
     */
    public static class Compaction {

        private boolean enabled = false;

        private boolean modelSummaryEnabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isModelSummaryEnabled() {
            return modelSummaryEnabled;
        }

        public void setModelSummaryEnabled(boolean modelSummaryEnabled) {
            this.modelSummaryEnabled = modelSummaryEnabled;
        }
    }
}
