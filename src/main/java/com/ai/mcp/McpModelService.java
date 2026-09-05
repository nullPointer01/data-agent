package com.ai.mcp;

import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunControl;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentEventType;
import com.ai.event.ModelConfigChangeEvent;
import com.ai.event.ModelConfigChangeEvent.ChangeType;
import com.ai.logging.StructuredLogger;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

        AgentRunControl runControl = beginAgentModelCall();
        try {
            ChatResponse response = invokeModelMessages(messages, modelId, modelKey, isDefault);
            String responseText = responseText(response);
            // token 记账优先使用厂商返回的真实用量
            long realInputTokens = realInputTokens(response, inputTokens);
            long outputTokens = realOutputTokens(response, responseText);
            long totalTokens = realInputTokens + outputTokens;
            settleAgentModelCall(runControl, response, totalTokens);

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
        return callModelInternal(prompt, modelId, false, true);
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
        return callModelInternal(prompt, modelId, true, true);
    }

    private String callModelInternal(String prompt, String modelId, boolean jsonMode, boolean governRun) {
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

        AgentRunControl runControl = governRun ? beginAgentModelCall() : null;
        try {
            ChatResponse response = invokeModel(prompt, modelId, modelKey, isDefault, jsonMode);
            String responseText = responseText(response);
            // token 记账优先使用厂商返回的真实用量
            long realInputTokens = realInputTokens(response, inputTokens);
            long outputTokens = realOutputTokens(response, responseText);
            long totalTokens = realInputTokens + outputTokens;
            settleAgentModelCall(runControl, response, totalTokens);
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
        AgentRunControl runControl = beginAgentModelCall();
        try {
            String result = streamWithLangChain(prompt, modelId, tokenConsumer);
            long outputTokens = tokenMonitor.estimateTokens(result);
            long totalTokens = inputTokens + outputTokens;
            settleAgentModelCall(runControl, null, totalTokens);
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
            String fallback = callModelInternal(prompt, modelId, false, false);
            settleAgentModelCall(runControl, null,
                    inputTokens + tokenMonitor.estimateTokens(fallback));
            tokenConsumer.accept(fallback);
            return fallback;
        }
    }

    private ChatResponse invokeModel(String prompt, String modelId, String modelKey, boolean isDefault)
            throws Exception {
        return invokeModel(prompt, modelId, modelKey, isDefault, false);
    }

    private ChatResponse invokeModel(String prompt, String modelId, String modelKey, boolean isDefault,
            boolean jsonMode) throws Exception {
        // 用消息形式调用以获取厂商返回的真实 token 用量
        ChatModel model = jsonMode
                ? modelClientRegistry.getJsonChatModel(isDefault ? null : modelId)
                : modelClientRegistry.getChatModel(isDefault ? null : modelId);
        return retryExecutor.execute(() -> model.chat(List.of(UserMessage.from(prompt))), modelKey);
    }

    private ChatResponse invokeModelMessages(List<ChatMessage> messages, String modelId, String modelKey,
            boolean isDefault) throws Exception {
        ChatModel model = modelClientRegistry.getChatModel(isDefault ? null : modelId);
        return retryExecutor.execute(() -> model.chat(messages), modelKey);
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
    public ChatResponse callMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecs,
            String modelId) {
        TokenQuotaGuard.QuotaCheckResult quotaResult = tokenQuotaGuard.checkCurrentUserQuota();
        if (!quotaResult.allowed()) {
            return ChatResponse.builder().aiMessage(AiMessage.from(quotaResult.message())).build();
        }
        String modelKey = modelClientRegistry.modelKey(modelId);
        long startTime = System.currentTimeMillis();
        AgentRunControl runControl = beginAgentModelCall();
        try {
            ChatModel model = modelClientRegistry.getChatModel(modelId);
            ChatRequest request = buildChatRequest(messages, toolSpecs);
            ChatResponse response = retryExecutor.execute(() -> model.chat(request), modelKey);
            recordMessagesUsage(messages, response, modelId, modelKey, startTime, runControl);
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
    public ChatResponse callMessagesStreaming(List<ChatMessage> messages, List<ToolSpecification> toolSpecs,
            String modelId, Consumer<String> tokenConsumer) {
        TokenQuotaGuard.QuotaCheckResult quotaResult = tokenQuotaGuard.checkCurrentUserQuota();
        if (!quotaResult.allowed()) {
            tokenConsumer.accept(quotaResult.message());
            return ChatResponse.builder().aiMessage(AiMessage.from(quotaResult.message())).build();
        }
        String modelKey = modelClientRegistry.modelKey(modelId);
        long startTime = System.currentTimeMillis();
        AgentRunControl runControl = beginAgentModelCall();
        try {
            StreamingChatModel model = modelClientRegistry.getStreamingChatModel(modelId);
            CompletableFuture<ChatResponse> completion = new CompletableFuture<>();
            StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    tokenConsumer.accept(token);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    completion.complete(response);
                }

                @Override
                public void onError(Throwable error) {
                    completion.completeExceptionally(error);
                }
            };
            model.chat(buildChatRequest(messages, toolSpecs), handler);
            // 上限兜底，防止厂商流中断且不回调 onError 时调用线程永久挂起
            ChatResponse response = completion.get(STREAMING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            recordMessagesUsage(messages, response, modelId, modelKey, startTime, runControl);
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

    private ChatRequest buildChatRequest(List<ChatMessage> messages, List<ToolSpecification> toolSpecs) {
        ChatRequest.Builder builder = ChatRequest.builder().messages(messages);
        if (hasTools(toolSpecs)) {
            builder.toolSpecifications(toolSpecs);
        }
        return builder.build();
    }

    private String responseText(ChatResponse response) {
        if (response == null || response.aiMessage() == null || response.aiMessage().text() == null) {
            return "";
        }
        return response.aiMessage().text();
    }

    private long realInputTokens(ChatResponse response, long estimatedInputTokens) {
        TokenUsage usage = response == null ? null : response.tokenUsage();
        return usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : estimatedInputTokens;
    }

    private long realOutputTokens(ChatResponse response, String responseText) {
        TokenUsage usage = response == null ? null : response.tokenUsage();
        return usage != null && usage.outputTokenCount() != null
                ? usage.outputTokenCount()
                : tokenMonitor.estimateTokens(responseText);
    }

    /**
     * 记录消息级调用的 token 用量：优先采用厂商返回的真实值，缺失时回退本地估算。
     */
    private void recordMessagesUsage(List<ChatMessage> messages, ChatResponse response,
            String modelId, String modelKey, long startTime, AgentRunControl runControl) {
        TokenUsage usage = response.tokenUsage();
        long inputTokens = usage != null && usage.inputTokenCount() != null
                ? usage.inputTokenCount()
                : estimateMessagesTokens(messages);
        String responseText = response.aiMessage() != null && response.aiMessage().text() != null
                ? response.aiMessage().text()
                : "";
        long outputTokens = usage != null && usage.outputTokenCount() != null
                ? usage.outputTokenCount()
                : tokenMonitor.estimateTokens(responseText);
        long totalTokens = inputTokens + outputTokens;
        settleAgentModelCall(runControl, response, totalTokens);
        tokenMonitor.recordModelTokenUsage(modelKey, totalTokens);
        boolean isDefault = modelClientRegistry.isDefaultModel(modelId);
        tokenUsageRecorder.record(isDefault ? null : modelId, null, inputTokens, outputTokens, totalTokens, null);
        structuredLogger.logLlmCall(null, resolveLogModelId(modelId, isDefault), inputTokens, outputTokens,
                System.currentTimeMillis() - startTime, true, null);
    }

    private AgentRunControl beginAgentModelCall() {
        AgentRunContext context = AgentRunScope.current().orElse(null);
        if (context == null) {
            return null;
        }
        context.control().beforeModelCall();
        return context.control();
    }

    private void settleAgentModelCall(AgentRunControl control, ChatResponse response, long totalTokens) {
        if (control == null) {
            return;
        }
        TokenUsage usage = response == null ? null : response.tokenUsage();
        boolean estimated = usage == null
                || usage.inputTokenCount() == null
                || usage.outputTokenCount() == null;
        control.afterModelCall(totalTokens, estimated);
        AgentRunContext context = AgentRunScope.current().orElse(null);
        if (context != null && context.eventSink().isStreaming()) {
            context.eventSink().emit(AgentEvent.of(context, AgentEventType.BUDGET_UPDATED,
                    Map.of("usage", context.snapshot())));
        }
    }

    private long estimateMessagesTokens(List<ChatMessage> messages) {
        long total = 0;
        for (ChatMessage message : messages) {
            total += tokenMonitor.estimateTokens(extractMessageText(message));
        }
        return total;
    }

    /**
     * 提取各类消息中的文本内容，避免使用已废弃的通用 {@code ChatMessage.text()}。
     *
     * @param message 对话消息
     * @return 可用于 token 估算的文本，非文本消息返回空字符串
     */
    private String extractMessageText(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.contents().stream()
                    .filter(TextContent.class::isInstance)
                    .map(TextContent.class::cast)
                    .map(TextContent::text)
                    .collect(Collectors.joining("\n"));
        }
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof AiMessage aiMessage) {
            return aiMessage.text() == null ? "" : aiMessage.text();
        }
        if (message instanceof ToolExecutionResultMessage toolResultMessage) {
            return toolResultMessage.text();
        }
        return "";
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
        StreamingChatModel model = modelClientRegistry.getStreamingChatModel(modelId);
        CompletableFuture<String> completion = new CompletableFuture<>();
        StringBuilder buffer = new StringBuilder();
        model.chat(prompt, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                buffer.append(token);
                tokenConsumer.accept(token);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
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
