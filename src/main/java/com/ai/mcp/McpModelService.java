package com.ai.mcp;

import com.ai.agent.context.AgentContextGovernor;
import com.ai.agent.context.AgentContextGovernor.ContextLimitException;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunControl;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.AgentRunTerminatedException;
import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentEventType;
import com.ai.event.ModelConfigChangeEvent;
import com.ai.event.ModelConfigChangeEvent.ChangeType;
import com.ai.config.TaskContextSnapshot;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.concurrent.Flow;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import org.reactivestreams.FlowAdapters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * 所有模型调用的统一治理入口：AiServices 和工具循环共享上下文、预算、重试与记账。
 * 失败以异常传播，流式记账留在发起请求的线程，保留租户和 Run 上下文。
 *
 * @author data-agent
 */
@Service
public class McpModelService {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpModelService.class);
    private final TokenMonitor tokenMonitor;
    private final ModelRetryExecutor retryExecutor;
    private final TokenUsageRecorder tokenUsageRecorder;
    private final ModelClientRegistry modelClientRegistry;
    private final TokenQuotaGuard tokenQuotaGuard;
    private final AgentContextGovernor contextGovernor;
    private final ModelStreamRuntime streamRuntime;
    private final ModelCallExecutor callExecutor;

    public McpModelService(TokenMonitor tokenMonitor,
            ModelRetryExecutor retryExecutor, TokenUsageRecorder tokenUsageRecorder,
            ModelClientRegistry modelClientRegistry, TokenQuotaGuard tokenQuotaGuard,
            AgentContextGovernor contextGovernor, ModelStreamRuntime streamRuntime,
            ModelCallExecutor callExecutor) {
        this.tokenMonitor = tokenMonitor;
        this.retryExecutor = retryExecutor;
        this.tokenUsageRecorder = tokenUsageRecorder;
        this.modelClientRegistry = modelClientRegistry;
        this.tokenQuotaGuard = tokenQuotaGuard;
        this.contextGovernor = contextGovernor;
        this.streamRuntime = streamRuntime;
        this.callExecutor = callExecutor;
    }

    /** 配置变更后移除旧客户端，下次按当前租户配置重建。 */
    @EventListener
    public void onModelConfigChange(ModelConfigChangeEvent event) {
        ChangeType type = event.getChangeType();
        if (type == ChangeType.UPDATED || type == ChangeType.TOGGLED || type == ChangeType.DELETED) {
            modelClientRegistry.refreshModelCache(event.getModelId());
        }
    }

    /** 提供给 AiServices 的同步适配器，所有请求仍经过统一治理。 */
    public ChatModel chatModel(String modelId) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return callRequest(request, modelId, true);
            }
        };
    }

    /** 声明式任务按返回类型请求原生 Schema；兼容模式显式请求 JSON Object，不自动降级重发。 */
    public ChatModel structuredChatModel(String modelId, boolean governRun, boolean nativeSchema) {
        return new ChatModel() {
            @Override
            public Set<Capability> supportedCapabilities() {
                return nativeSchema ? Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA) : Set.of();
            }

            @Override
            public ChatRequestParameters defaultRequestParameters() {
                return ChatRequestParameters.builder().responseFormat(nativeSchema ? null : ResponseFormat.JSON).build();
            }

            @Override
            public ChatResponse doChat(ChatRequest request) {
                return callRequest(request, modelId, governRun);
            }
        };
    }

    /**
     * 提供给 AiServices 的流式适配器，框架负责事件 Flux，治理层负责预算与终态。
     * start 后立即返回，最终记账与完成事件在恢复身份的回调中执行。
     */
    public StreamingChatModel streamingChatModel(String modelId) {
        TaskContextSnapshot owner = TaskContextSnapshot.capture();
        return new StreamingChatModel() {
            /** AiServices 的事件 Flux 使用响应式 SPI；仍复用同一模型预算、记账和传输取消。 */
            @Override
            public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest request) {
                var events = Flux.<ChatModelStreamingEvent>create(sink -> owner.wrap(() -> {
                            var future = callStreamingRequestAsync(request, modelId, token -> {
                                if (token != null && !token.isEmpty()) {
                                    sink.next(new PartialResponse(token));
                                }
                            });
                            sink.onCancel(() -> future.cancel(true));
                            future.whenComplete((response, error) -> {
                                if (error != null) {
                                    sink.error(unwrap(error));
                                } else {
                                    sink.next(new CompleteResponse(response));
                                    sink.complete();
                                }
                            });
                        }).run(), FluxSink.OverflowStrategy.ERROR)
                        .onBackpressureBuffer(256);
                return FlowAdapters.toFlowPublisher(events);
            }

            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                callStreamingRequestAsync(request, modelId, handler::onPartialResponse)
                        .whenComplete((response, error) -> {
                            if (error != null) {
                                handler.onError(unwrap(error));
                            } else {
                                // 工具预检/框架回调可能暂停或失败，必须交给 onError，避免 Future 永不完成。
                                try {
                                    handler.onCompleteResponse(response);
                                } catch (Exception failure) {
                                    handler.onError(failure);
                                }
                            }
                        });
            }
        };
    }

    /** 使用默认模型执行文本任务。 */
    public String callModel(String prompt) {
        return callModel(prompt, null);
    }

    /** 使用指定模型执行无持久会话的文本任务。 */
    public String callModel(String prompt, String modelId) {
        return callText(prompt, modelId, null, true);
    }

    /** 请求 JSON Object，语义和字段校验仍由业务调用方负责。 */
    public String callModelJson(String prompt, String modelId) {
        return callText(prompt, modelId, ResponseFormat.JSON, true);
    }

    private String callText(String prompt, String modelId, ResponseFormat format, boolean governRun) {
        ChatRequest.Builder request = ChatRequest.builder().messages(UserMessage.from(prompt));
        if (format != null) {
            request.responseFormat(format);
        }
        return responseText(callRequest(request.build(), modelId, governRun));
    }

    /** 通过原生消息与工具协议执行一轮模型请求。 */
    public ChatResponse callMessages(List<ChatMessage> messages, List<ToolSpecification> tools, String modelId) {
        return callMessages(messages, tools, modelId, ToolChoice.AUTO);
    }

    /** 保留首轮工具选择约束，同步调用与普通聊天共享治理。 */
    public ChatResponse callMessages(List<ChatMessage> messages, List<ToolSpecification> tools,
            String modelId, ToolChoice toolChoice) {
        return callRequest(request(messages, tools, toolChoice), modelId, true);
    }

    /** 流式返回文本，完整响应保留工具请求与厂商 TokenUsage。 */
    public ChatResponse callMessagesStreaming(List<ChatMessage> messages, List<ToolSpecification> tools,
            String modelId, Consumer<String> tokenConsumer) {
        return callStreamingRequest(request(messages, tools, ToolChoice.AUTO), modelId, tokenConsumer);
    }

    private ChatRequest request(List<ChatMessage> messages, List<ToolSpecification> tools, ToolChoice choice) {
        ChatRequest.Builder builder = ChatRequest.builder().messages(messages);
        if (tools != null && !tools.isEmpty()) {
            builder.toolSpecifications(tools).toolChoice(choice == null ? ToolChoice.AUTO : choice);
        }
        return builder.build();
    }

    private ChatResponse callRequest(ChatRequest source, String modelId, boolean governRun) {
        checkQuota();
        var governed = contextGovernor.govern(source.messages(), source.toolSpecifications(), modelId);
        // 原生复制 API 保留 responseFormat、toolChoice 及厂商参数，只替换准入后的消息。
        ChatRequest request = source.toBuilder().messages(governed.messages()).build();
        AgentRunControl control = currentControl(governRun);
        String modelKey = modelClientRegistry.modelKey(modelId);
        try {
            ChatModel model = modelClientRegistry.getChatModel(modelId);
            ChatResponse response = callExecutor.call(() -> retryExecutor.execute(() -> {
                if (control != null) {
                    control.beforeModelCall();
                }
                return model.chat(request);
            }, modelKey), control);
            validateResponse(response);
            recordUsage(response, governed.evidence().estimatedTokensAfter(), modelId, control);
            return response;
        } catch (Exception exception) {
            throw failure(exception, modelKey, control);
        }
    }

    private ChatResponse callStreamingRequest(ChatRequest source, String modelId, Consumer<String> consumer) {
        CompletableFuture<ChatResponse> future = callStreamingRequestAsync(source, modelId, consumer);
        try {
            return future.get();
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ModelInvocationException("模型调用已中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = unwrap(exception);
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ModelInvocationException("模型流执行失败", cause);
        }
    }

    private CompletableFuture<ChatResponse> callStreamingRequestAsync(ChatRequest source, String modelId,
            Consumer<String> consumer) {
        try {
            Objects.requireNonNull(consumer, "流式输出消费者不能为空");
            checkQuota();
            var governed = contextGovernor.govern(source.messages(), source.toolSpecifications(), modelId);
            ChatRequest request = source.toBuilder().messages(governed.messages()).build();
            AgentRunControl control = currentControl(true);
            String modelKey = modelClientRegistry.modelKey(modelId);
            TaskContextSnapshot snapshot = TaskContextSnapshot.capture();
            ModelResponseStream stream = streamRuntime.open(
                    token -> snapshot.call(() -> {
                        consumer.accept(token);
                        return null;
                    }),
                    control);
            var responseStage = retryExecutor.executeStream(() -> {
                try {
                    if (control != null) {
                        control.beforeModelCall();
                    }
                    modelClientRegistry.getStreamingChatModel(modelId).chat(request, stream);
                } catch (Exception exception) {
                    stream.onError(exception);
                }
                return stream.completion();
            }, modelKey);
            CompletableFuture<ChatResponse> result = new CompletableFuture<>();
            // 调用方取消等待句柄时，仍必须对实际流执行记账与释放，不能取消完成处理本身。
            responseStage.whenCompleteAsync((response, error) -> {
                try {
                    if (error != null) {
                        stream.onError(unwrap(error));
                        recordPartialUsage(stream.partialText(), governed.evidence().estimatedTokensAfter(), modelId, control);
                        throw failure(unwrap(error), modelKey, control);
                    }
                    validateResponse(response);
                    recordUsage(response, governed.evidence().estimatedTokensAfter(), modelId, control);
                    result.complete(response);
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                } finally {
                    stream.close();
                }
            }, streamRuntime.callbacks(snapshot));
            result.whenComplete((response, error) -> {
                if (result.isCancelled()) {
                    stream.onError(new java.util.concurrent.CancellationException("模型流被取消"));
                }
            });
            return result;
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private Throwable unwrap(Throwable error) {
        while ((error instanceof ExecutionException || error instanceof CompletionException) && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private void checkQuota() {
        var quota = tokenQuotaGuard.checkCurrentUserQuota();
        if (!quota.allowed()) {
            throw new ModelInvocationException(quota.message());
        }
    }

    private void recordPartialUsage(String partialText, long input, String modelId, AgentRunControl control) {
        if (partialText.isEmpty()) {
            return;
        }
        long output = tokenMonitor.estimateTokens(partialText, modelId);
        tokenUsageRecorder.record(modelId, null, input, output, input + output, null);
        if (control != null) {
            control.afterModelCall(input + output, true);
        }
    }

    private AgentRunControl currentControl(boolean governRun) {
        return governRun ? AgentRunScope.current().map(AgentRunContext::control).orElse(null) : null;
    }

    private void validateResponse(ChatResponse response) {
        if (response == null || response.aiMessage() == null
                || (!response.aiMessage().hasToolExecutionRequests()
                && (response.aiMessage().text() == null || response.aiMessage().text().isBlank()))) {
            throw new ModelInvocationException("模型未返回有效回答");
        }
    }

    private String responseText(ChatResponse response) {
        if (response.aiMessage().hasToolExecutionRequests()) {
            throw new ModelInvocationException("当前文本任务收到了未预期的工具调用");
        }
        return response.aiMessage().text();
    }

    private void recordUsage(ChatResponse response, long estimatedInput, String modelId,
            AgentRunControl control) {
        TokenUsage usage = response.tokenUsage();
        long input = usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : estimatedInput;
        String text = response.aiMessage().text() == null ? "" : response.aiMessage().text();
        String toolArguments = response.aiMessage().hasToolExecutionRequests()
                ? response.aiMessage().toolExecutionRequests().toString() : "";
        long output = usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount()
                : tokenMonitor.estimateTokens(text + toolArguments, modelId);
        long total = usage != null && usage.totalTokenCount() != null ? usage.totalTokenCount() : input + output;
        tokenUsageRecorder.record(modelId, null, input, output, total, null);
        if (control != null) {
            boolean estimated = usage == null || usage.inputTokenCount() == null || usage.outputTokenCount() == null;
            control.afterModelCall(total, estimated);
            AgentRunScope.current().ifPresent(context -> context.eventSink().emit(
                    AgentEvent.of(context, AgentEventType.BUDGET_UPDATED, Map.of("usage", context.snapshot()))));
            control.ensureActive();
        }
    }

    private RuntimeException failure(Throwable exception, String modelKey, AgentRunControl control) {
        Throwable cause = unwrap(exception);
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        if (cause instanceof AgentRunTerminatedException terminated) {
            return terminated;
        }
        if (cause instanceof ContextLimitException limit) {
            return limit;
        }
        if (control != null) {
            control.ensureActive();
        }
        if (cause instanceof ModelInvocationException invocation) {
            return invocation;
        }
        LOGGER.warn("模型请求失败 | model={} | errorType={}", modelKey, cause.getClass().getSimpleName());
        String message = cause instanceof TimeoutException ? "模型响应超时，请稍后重试"
                : cause instanceof InterruptedException ? "模型调用已中断" : "模型调用失败，请检查模型配置或稍后重试";
        return new ModelInvocationException(message, cause);
    }
}
