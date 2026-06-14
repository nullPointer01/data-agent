package com.ai.mcp;

import com.ai.event.ModelConfigChangeEvent;
import com.ai.event.ModelConfigChangeEvent.ChangeType;
import com.ai.logging.StructuredLogger;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 模型调用门面，统一编排上下文、配额、重试和 token 记账。
 *
 * @author data-agent
 */
@Service
public class McpModelService {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpModelService.class);
    private static final int STREAMING_TIMEOUT_SECONDS = 300;
    private static final String DEFAULT_MODEL_KEY = "default";
    private static final String DIRECT_SKILL_ID = "direct";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ERROR_CIRCUIT_PREFIX = "模型[";
    private static final String ERROR_MODEL_CALL_PREFIX = "模型调用失败: ";

    private final McpContextManager contextManager;
    private final TokenMonitor tokenMonitor;
    private final ModelRetryExecutor retryExecutor;
    private final TokenUsageRecorder tokenUsageRecorder;
    private final ModelClientRegistry modelClientRegistry;
    private final TokenQuotaGuard tokenQuotaGuard;
    private final StructuredLogger structuredLogger;

    public McpModelService(
            McpContextManager contextManager,
            TokenMonitor tokenMonitor,
            ModelRetryExecutor retryExecutor,
            TokenUsageRecorder tokenUsageRecorder,
            ModelClientRegistry modelClientRegistry,
            TokenQuotaGuard tokenQuotaGuard,
            StructuredLogger structuredLogger) {
        this.contextManager = contextManager;
        this.tokenMonitor = tokenMonitor;
        this.retryExecutor = retryExecutor;
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
        // ChatMemory 窗口已包含本次 user 消息，按消息对象传给模型，保留角色结构
        List<ChatMessage> messages = context.historyMessages();
        long inputTokens = estimateMessagesTokens(messages);
        boolean isDefault = modelClientRegistry.isDefaultModel(modelId);
        String modelKey = modelClientRegistry.modelKey(modelId);
        long startTime = System.currentTimeMillis();
        LOGGER.info("Calling model with context | modelId={} | isDefault={} | historyMessages={} | promptTokens={} | contextId={}",
                isDefault ? DEFAULT_MODEL_KEY : modelId, isDefault, messages.size(), inputTokens, contextId);

        try {
            Response<AiMessage> response = invokeModelMessages(messages, modelId, modelKey, isDefault);
            String responseText = responseText(response);
            // token 记账优先使用厂商返回的真实用量
            long realInputTokens = realInputTokens(response, inputTokens);
            long outputTokens = realOutputTokens(response, responseText);
            long totalTokens = realInputTokens + outputTokens;

            contextManager.addConversationTurn(contextId, ROLE_ASSISTANT, responseText);
            tokenMonitor.recordSkillTokenUsage(skillId != null ? skillId : DIRECT_SKILL_ID, totalTokens);
            tokenMonitor.recordModelTokenUsage(modelKey, totalTokens);
            tokenUsageRecorder.record(modelId, skillId, realInputTokens, outputTokens, totalTokens, null);
            structuredLogger.logLlmCall(contextId, resolveLogModelId(modelId, isDefault), realInputTokens, outputTokens,
                    System.currentTimeMillis() - startTime, true, null);
            return responseText;
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
        return callModelInternal(prompt, modelId, false);
    }

    /**
     * 以 JSON 强制输出模式调用模型（response_format=json_object），用于结构化输出场景。
     *
     * <p>注意：提示词中必须包含 "JSON" 字样（OpenAI 协议要求）。</p>
     *
     * @param prompt 提示词
     * @param modelId 模型编号
     * @return 模型响应（合法 JSON 文本）
     */
    public String callModelJson(String prompt, String modelId) {
        return callModelInternal(prompt, modelId, true);
    }

    private String callModelInternal(String prompt, String modelId, boolean jsonMode) {
        TokenQuotaGuard.QuotaCheckResult quotaResult = tokenQuotaGuard.checkCurrentUserQuota();
        if (!quotaResult.allowed()) {
            return quotaResult.message();
        }

        long inputTokens = tokenMonitor.estimateTokens(prompt);
        boolean isDefault = modelClientRegistry.isDefaultModel(modelId);
        String modelKey = modelClientRegistry.modelKey(modelId);
        long startTime = System.currentTimeMillis();
        LOGGER.info("Calling model | modelId={} | isDefault={} | jsonMode={} | promptLength={} | promptTokens={}",
                isDefault ? DEFAULT_MODEL_KEY : modelId, isDefault, jsonMode, prompt.length(), inputTokens);

        try {
            Response<AiMessage> response = invokeModel(prompt, modelId, modelKey, isDefault, jsonMode);
            String responseText = responseText(response);
            // token 记账优先使用厂商返回的真实用量
            long realInputTokens = realInputTokens(response, inputTokens);
            long outputTokens = realOutputTokens(response, responseText);
            long totalTokens = realInputTokens + outputTokens;
            tokenMonitor.recordModelTokenUsage(modelKey, totalTokens);
            tokenUsageRecorder.record(isDefault ? null : modelId, null, realInputTokens, outputTokens, totalTokens,
                    null);
            structuredLogger.logLlmCall(null, resolveLogModelId(modelId, isDefault), realInputTokens, outputTokens,
                    System.currentTimeMillis() - startTime, true, null);
            return responseText;
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
            String result = streamWithLangChain(prompt, modelId, tokenConsumer);
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

    private Response<AiMessage> invokeModel(String prompt, String modelId, String modelKey, boolean isDefault)
            throws Exception {
        return invokeModel(prompt, modelId, modelKey, isDefault, false);
    }

    private Response<AiMessage> invokeModel(String prompt, String modelId, String modelKey, boolean isDefault,
            boolean jsonMode) throws Exception {
        // 用消息形式调用以获取厂商返回的真实 token 用量
        ChatLanguageModel model = jsonMode
                ? modelClientRegistry.getJsonChatModel(isDefault ? null : modelId)
                : modelClientRegistry.getChatModel(isDefault ? null : modelId);
        return retryExecutor.execute(() -> model.generate(List.of(UserMessage.from(prompt))), modelKey);
    }

    private Response<AiMessage> invokeModelMessages(List<ChatMessage> messages, String modelId, String modelKey,
            boolean isDefault) throws Exception {
        ChatLanguageModel model = modelClientRegistry.getChatModel(isDefault ? null : modelId);
        return retryExecutor.execute(() -> model.generate(messages), modelKey);
    }

    /**
     * 以多轮消息和工具规格调用模型（LangChain4j 原生协议）。
     *
     * <p>消息角色结构原样传给厂商（system/user/assistant/tool），工具调用走原生
     * Function Calling；token 记账优先使用厂商返回的真实用量，缺失时回退估算。</p>
     *
     * @param messages 多轮消息历史
     * @param toolSpecs 工具规格，可为 null 表示无工具
     * @param modelId 模型编号，空值表示默认模型
     * @return 模型响应（含 AiMessage 与 token 用量）；配额不足或调用失败时返回 null
     */
    public Response<AiMessage> callMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecs,
            String modelId) {
        TokenQuotaGuard.QuotaCheckResult quotaResult = tokenQuotaGuard.checkCurrentUserQuota();
        if (!quotaResult.allowed()) {
            return Response.from(AiMessage.from(quotaResult.message()));
        }
        String modelKey = modelClientRegistry.modelKey(modelId);
        long startTime = System.currentTimeMillis();
        try {
            ChatLanguageModel model = modelClientRegistry.getChatModel(modelId);
            Response<AiMessage> response = retryExecutor.execute(
                    () -> hasTools(toolSpecs) ? model.generate(messages, toolSpecs) : model.generate(messages),
                    modelKey);
            recordMessagesUsage(messages, response, modelId, modelKey, startTime);
            return response;
        } catch (Exception e) {
            LOGGER.error("Message-based model call failed | modelId={}", modelKey, e);
            structuredLogger.logLlmCall(null, modelKey, 0L, 0L,
                    System.currentTimeMillis() - startTime, false, e.getMessage());
            return null;
        }
    }

    /**
     * 以多轮消息和工具规格流式调用模型，文本 token 实时转发，工具调用在完成回调中返回。
     *
     * @param messages 多轮消息历史
     * @param toolSpecs 工具规格，可为 null 表示无工具
     * @param modelId 模型编号，空值表示默认模型
     * @param tokenConsumer 文本 token 消费者
     * @return 模型响应；配额不足或调用失败时返回 null
     */
    public Response<AiMessage> callMessagesStreaming(List<ChatMessage> messages, List<ToolSpecification> toolSpecs,
            String modelId, Consumer<String> tokenConsumer) {
        TokenQuotaGuard.QuotaCheckResult quotaResult = tokenQuotaGuard.checkCurrentUserQuota();
        if (!quotaResult.allowed()) {
            tokenConsumer.accept(quotaResult.message());
            return Response.from(AiMessage.from(quotaResult.message()));
        }
        String modelKey = modelClientRegistry.modelKey(modelId);
        long startTime = System.currentTimeMillis();
        try {
            StreamingChatLanguageModel model = modelClientRegistry.getStreamingChatModel(modelId);
            CompletableFuture<Response<AiMessage>> completion = new CompletableFuture<>();
            StreamingResponseHandler<AiMessage> handler = new StreamingResponseHandler<>() {
                @Override
                public void onNext(String token) {
                    tokenConsumer.accept(token);
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    completion.complete(response);
                }

                @Override
                public void onError(Throwable error) {
                    completion.completeExceptionally(error);
                }
            };
            if (hasTools(toolSpecs)) {
                model.generate(messages, toolSpecs, handler);
            } else {
                model.generate(messages, handler);
            }
            // 上限兜底，防止厂商流中断且不回调 onError 时调用线程永久挂起
            Response<AiMessage> response = completion.get(STREAMING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            recordMessagesUsage(messages, response, modelId, modelKey, startTime);
            return response;
        } catch (Exception e) {
            LOGGER.error("Message-based streaming call failed | modelId={}", modelKey, e);
            structuredLogger.logLlmCall(null, modelKey, 0L, 0L,
                    System.currentTimeMillis() - startTime, false, e.getMessage());
            return null;
        }
    }

    private boolean hasTools(List<ToolSpecification> toolSpecs) {
        return toolSpecs != null && !toolSpecs.isEmpty();
    }

    private String responseText(Response<AiMessage> response) {
        if (response == null || response.content() == null || response.content().text() == null) {
            return "";
        }
        return response.content().text();
    }

    private long realInputTokens(Response<AiMessage> response, long estimatedInputTokens) {
        TokenUsage usage = response == null ? null : response.tokenUsage();
        return usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : estimatedInputTokens;
    }

    private long realOutputTokens(Response<AiMessage> response, String responseText) {
        TokenUsage usage = response == null ? null : response.tokenUsage();
        return usage != null && usage.outputTokenCount() != null
                ? usage.outputTokenCount()
                : tokenMonitor.estimateTokens(responseText);
    }

    /**
     * 记录消息级调用的 token 用量：优先采用厂商返回的真实值，缺失时回退本地估算。
     */
    private void recordMessagesUsage(List<ChatMessage> messages, Response<AiMessage> response,
            String modelId, String modelKey, long startTime) {
        TokenUsage usage = response.tokenUsage();
        long inputTokens = usage != null && usage.inputTokenCount() != null
                ? usage.inputTokenCount()
                : estimateMessagesTokens(messages);
        String responseText = response.content() != null && response.content().text() != null
                ? response.content().text()
                : "";
        long outputTokens = usage != null && usage.outputTokenCount() != null
                ? usage.outputTokenCount()
                : tokenMonitor.estimateTokens(responseText);
        long totalTokens = inputTokens + outputTokens;
        tokenMonitor.recordModelTokenUsage(modelKey, totalTokens);
        boolean isDefault = modelClientRegistry.isDefaultModel(modelId);
        tokenUsageRecorder.record(isDefault ? null : modelId, null, inputTokens, outputTokens, totalTokens, null);
        structuredLogger.logLlmCall(null, resolveLogModelId(modelId, isDefault), inputTokens, outputTokens,
                System.currentTimeMillis() - startTime, true, null);
    }

    private long estimateMessagesTokens(List<ChatMessage> messages) {
        long total = 0;
        for (ChatMessage message : messages) {
            total += tokenMonitor.estimateTokens(message.text());
        }
        return total;
    }

    /**
     * 通过 LangChain4j 流式客户端调用模型，将 token 实时转发给消费者并阻塞至生成完成。
     *
     * @param prompt 提示词
     * @param modelId 模型编号
     * @param tokenConsumer token 片段消费者
     * @return 完整响应文本
     * @throws Exception 流式调用失败或超时
     */
    private String streamWithLangChain(String prompt, String modelId, Consumer<String> tokenConsumer)
            throws Exception {
        StreamingChatLanguageModel model = modelClientRegistry.getStreamingChatModel(modelId);
        CompletableFuture<String> completion = new CompletableFuture<>();
        StringBuilder buffer = new StringBuilder();
        model.generate(prompt, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                buffer.append(token);
                tokenConsumer.accept(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                completion.complete(buffer.toString());
            }

            @Override
            public void onError(Throwable error) {
                completion.completeExceptionally(error);
            }
        });
        // 上限兜底，防止厂商流中断且不回调 onError 时调用线程永久挂起
        return completion.get(STREAMING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private String resolveLogModelId(String modelId, boolean isDefault) {
        return isDefault ? DEFAULT_MODEL_KEY : modelId;
    }

    private String formatModelException(Exception exception) {
        String message = exception.getMessage();
        if (message != null && message.startsWith(ERROR_CIRCUIT_PREFIX)) {
            return message;
        }
        return ERROR_MODEL_CALL_PREFIX + message;
    }
}
