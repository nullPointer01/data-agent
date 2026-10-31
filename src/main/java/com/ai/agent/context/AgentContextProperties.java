package com.ai.agent.context;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
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
    private Map<String, String> modelTokenizers = new LinkedHashMap<>();

    public Map<String, String> getModelTokenizers() { return modelTokenizers; }
    public void setModelTokenizers(Map<String, String> values) {
        modelTokenizers = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    @Valid
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

    /** 模型摘要只允许在完整上下文治理开启时运行。 */
    @AssertTrue(message = "启用模型摘要必须同时开启 app.agent.context.compaction.enabled")
    public boolean isModelSummarySwitchValid() {
        return !compaction.isModelSummaryEnabled() || compaction.isEnabled();
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
     * 超限才生成有证据的任务摘录，失败时仍保留协议组裁剪。
     */
    public static class Compaction {

        private boolean enabled = false;

        private boolean modelSummaryEnabled = false;
        @Min(1024) @Max(16384)
        private int summaryInputTokens = 8192;
        @Min(128) @Max(2048)
        private int summaryOutputTokens = 768;
        @Min(256) @Max(8192)
        private int minimumSavingsTokens = 1024;
        @Min(1) @Max(4)
        private int maxSummariesPerRun = 2;

        public int getSummaryInputTokens() { return summaryInputTokens; }
        public void setSummaryInputTokens(int value) { summaryInputTokens = value; }
        public int getSummaryOutputTokens() { return summaryOutputTokens; }
        public void setSummaryOutputTokens(int value) { summaryOutputTokens = value; }
        public int getMinimumSavingsTokens() { return minimumSavingsTokens; }
        public void setMinimumSavingsTokens(int value) { minimumSavingsTokens = value; }
        public int getMaxSummariesPerRun() { return maxSummariesPerRun; }
        public void setMaxSummariesPerRun(int value) { maxSummariesPerRun = value; }

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
