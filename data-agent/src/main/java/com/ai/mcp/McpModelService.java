package com.ai.mcp;

import com.ai.event.ModelConfigChangeEvent;
import com.ai.event.ModelConfigChangeEvent.ChangeType;
import com.ai.logging.StructuredLogger;
import com.ai.model.ModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 模型调用门面，统一编排上下文、配额、重试和 token 记账。
 *
 * @author data-agent
 */
@Service
public class McpModelService {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpModelService.class);
    private static final int DEFAULT_CONTEXT_HISTORY_TURNS = 5;
    private static final String DEFAULT_MODEL_KEY = "default";
    private static final String DIRECT_SKILL_ID = "direct";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ERROR_CIRCUIT_PREFIX = "模型[";
    private static final String ERROR_MODEL_CALL_PREFIX = "模型调用失败: ";
    private static final String HISTORY_CURRENT_REQUEST_HEADER = "\n## 当前请求\n";

    private final McpContextManager contextManager;
    private final TokenMonitor tokenMonitor;
    private final ModelRetryExecutor retryExecutor;
    private final ModelHttpClient modelHttpClient;
    private final TokenUsageRecorder tokenUsageRecorder;
    private final ModelClientRegistry modelClientRegistry;
    private final TokenQuotaGuard tokenQuotaGuard;
    private final StructuredLogger structuredLogger;

    public McpModelService(
            McpContextManager contextManager,
            TokenMonitor tokenMonitor,
            ModelRetryExecutor retryExecutor,
            ModelHttpClient modelHttpClient,
            TokenUsageRecorder tokenUsageRecorder,
            ModelClientRegistry modelClientRegistry,
            TokenQuotaGuard tokenQuotaGuard,
            StructuredLogger structuredLogger) {
        this.contextManager = contextManager;
        this.tokenMonitor = tokenMonitor;
        this.retryExecutor = retryExecutor;
        this.modelHttpClient = modelHttpClient;
        this.tokenUsageRecorder = tokenUsageRecorder;
        this.modelClientRegistry = modelClientRegistry;
        this.tokenQuotaGuard = tokenQuotaGuard;
        this.structuredLogger = structuredLogger;
        LOGGER.info("McpModelService initialized");
    }

    @EventListener
    public void onModelConfigChange(ModelConfigChangeEvent event) {
        String modelId = event.getModelId();
        ChangeType changeType = event.getChangeType();
        if (changeType == ChangeType.UPDATED || changeType == ChangeType.TOGGLED || changeType == ChangeType.DELETED) {
            modelClientRegistry.refreshModelCache(modelId);
            LOGGER.info("Model cache refreshed: {} ({})", modelId, changeType);
        }
    }

    public String callModelWithContext(String contextId, String prompt) {
        return callModelWithContext(contextId, prompt, null, null);
    }

    public String callModelWithContext(String contextId, String prompt, String modelId) {
        return callModelWithContext(contextId, prompt, modelId, null);
    }

    public String callModelWithContext(String contextId, String prompt, String modelId, String skillId) {
        McpContext context = contextManager.getContext(contextId);
        if (context == null) {
            throw new IllegalArgumentException("Context not found: " + contextId);
        }

        TokenQuotaGuard.QuotaCheckResult quotaResult = tokenQuotaGuard.checkCurrentUserQuota();
        if (!quotaResult.allowed()) {
            return quotaResult.message();
        }

        contextManager.addConversationTurn(contextId, ROLE_USER, prompt);
        String enhancedPrompt = buildEnhancedPrompt(context, prompt);
        long inputTokens = tokenMonitor.estimateTokens(enhancedPrompt);
        boolean isDefault = modelClientRegistry.isDefaultModel(modelId);
        String modelKey = modelClientRegistry.modelKey(modelId);
        long startTime = System.currentTimeMillis();
        LOGGER.info("Calling model with context | modelId={} | isDefault={} | promptLength={} | promptTokens={} | contextId={}",
                isDefault ? DEFAULT_MODEL_KEY : modelId, isDefault, enhancedPrompt.length(), inputTokens, contextId);

        try {
            String response = invokeModel(enhancedPrompt, modelId, modelKey, isDefault);
            long outputTokens = tokenMonitor.estimateTokens(response);
            long totalTokens = inputTokens + outputTokens;

            contextManager.addConversationTurn(contextId, ROLE_ASSISTANT, response);
            tokenMonitor.recordSkillTokenUsage(skillId != null ? skillId : DIRECT_SKILL_ID, totalTokens);
            tokenMonitor.recordModelTokenUsage(modelKey, totalTokens);
            tokenUsageRecorder.record(modelId, skillId, inputTokens, outputTokens, totalTokens, null);
            structuredLogger.logLlmCall(contextId, resolveLogModelId(modelId, isDefault), inputTokens, outputTokens,
                    System.currentTimeMillis() - startTime, true, null);
            return response;
        } catch (Exception e) {
            LOGGER.error("Model call with context failed", e);
            structuredLogger.logLlmCall(contextId, resolveLogModelId(modelId, isDefault), inputTokens, 0L,
                    System.currentTimeMillis() - startTime, false, e.getMessage());
            return formatModelException(e);
        }
    }

    public String callModel(String prompt) {
        return callModel(prompt, null);
    }

    public String callModel(String prompt, String modelId) {
        TokenQuotaGuard.QuotaCheckResult quotaResult = tokenQuotaGuard.checkCurrentUserQuota();
        if (!quotaResult.allowed()) {
            return quotaResult.message();
        }

        long inputTokens = tokenMonitor.estimateTokens(prompt);
        boolean isDefault = modelClientRegistry.isDefaultModel(modelId);
        String modelKey = modelClientRegistry.modelKey(modelId);
        long startTime = System.currentTimeMillis();
        LOGGER.info("Calling model | modelId={} | isDefault={} | promptLength={} | promptTokens={}",
                isDefault ? DEFAULT_MODEL_KEY : modelId, isDefault, prompt.length(), inputTokens);

        try {
            String response = invokeModel(prompt, modelId, modelKey, isDefault);
            long outputTokens = tokenMonitor.estimateTokens(response);
            long totalTokens = inputTokens + outputTokens;
            tokenMonitor.recordModelTokenUsage(modelKey, totalTokens);
            tokenUsageRecorder.record(isDefault ? null : modelId, null, inputTokens, outputTokens, totalTokens, null);
            structuredLogger.logLlmCall(null, resolveLogModelId(modelId, isDefault), inputTokens, outputTokens,
                    System.currentTimeMillis() - startTime, true, null);
            return response;
        } catch (Exception e) {
            LOGGER.error("{} model call failed after retries", isDefault ? "Default" : "Custom", e);
            structuredLogger.logLlmCall(null, resolveLogModelId(modelId, isDefault), inputTokens, 0L,
                    System.currentTimeMillis() - startTime, false, e.getMessage());
            return formatModelException(e);
        }
    }

    /**
     * 以流式方式调用模型，并返回完整响应文本。
     *
     * @param prompt 提示词
     * @param modelId 模型编号
     * @param tokenConsumer token 片段消费者
     * @return 完整响应
     */
    public String callModelStreaming(String prompt, String modelId, Consumer<String> tokenConsumer) {
        TokenQuotaGuard.QuotaCheckResult quotaResult = tokenQuotaGuard.checkCurrentUserQuota();
        if (!quotaResult.allowed()) {
            tokenConsumer.accept(quotaResult.message());
            return quotaResult.message();
        }

        long inputTokens = tokenMonitor.estimateTokens(prompt);
        boolean isDefault = modelClientRegistry.isDefaultModel(modelId);
        long startTime = System.currentTimeMillis();
        try {
            String result = modelHttpClient.callStreaming(prompt, resolveModelConfig(modelId), tokenConsumer);
            long outputTokens = tokenMonitor.estimateTokens(result);
            long totalTokens = inputTokens + outputTokens;
            String modelKey = modelClientRegistry.modelKey(modelId);
            tokenMonitor.recordModelTokenUsage(modelKey, totalTokens);
            tokenUsageRecorder.record(isDefault ? null : modelId, null,
                    inputTokens, outputTokens, totalTokens, null);
            structuredLogger.logLlmCall(null, resolveLogModelId(modelId, isDefault), inputTokens, outputTokens,
                    System.currentTimeMillis() - startTime, true, null);
            return result;
        } catch (Exception e) {
            LOGGER.error("Streaming model call failed, falling back to sync", e);
            structuredLogger.logLlmCall(null, resolveLogModelId(modelId, isDefault), inputTokens, 0L,
                    System.currentTimeMillis() - startTime, false, e.getMessage());
            String fallback = callModel(prompt, modelId);
            tokenConsumer.accept(fallback);
            return fallback;
        }
    }

    private String invokeModel(String prompt, String modelId, String modelKey, boolean isDefault) throws Exception {
        ModelConfig config = resolveModelConfig(isDefault ? null : modelId);
        if (isDefault || modelClientRegistry.isHttpOnlyModel(config)) {
            return retryExecutor.execute(() -> modelHttpClient.call(prompt, config), modelKey);
        }
        var model = modelClientRegistry.getChatModel(modelId);
        return retryExecutor.execute(() -> model.generate(prompt), modelKey);
    }

    private ModelConfig resolveModelConfig(String modelId) {
        return modelClientRegistry.resolveHttpConfig(modelId, modelHttpClient.defaultConfig());
    }

    private String resolveLogModelId(String modelId, boolean isDefault) {
        return isDefault ? DEFAULT_MODEL_KEY : modelId;
    }

    private String buildEnhancedPrompt(McpContext context, String prompt) {
        String history = context.buildHistoryPrompt(DEFAULT_CONTEXT_HISTORY_TURNS);
        if (history.isEmpty()) {
            return prompt;
        }
        return history + HISTORY_CURRENT_REQUEST_HEADER + prompt;
    }

    private String formatModelException(Exception exception) {
        String message = exception.getMessage();
        if (message != null && message.startsWith(ERROR_CIRCUIT_PREFIX)) {
            return message;
        }
        return ERROR_MODEL_CALL_PREFIX + message;
    }
}
