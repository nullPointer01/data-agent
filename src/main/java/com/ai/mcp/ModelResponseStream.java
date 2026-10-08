package com.ai.mcp;

import com.ai.agent.runtime.AgentRunControl;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/**
 * SDK 流的异步完成句柄；共享定时器检查预算/取消，终态后忽略迟到回调并释放传输句柄。
 */
final class ModelResponseStream implements StreamingChatResponseHandler, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelResponseStream.class);
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(300);
    private final Consumer<String> consumer;
    private final AgentRunControl control;
    private final CompletableFuture<ChatResponse> completion = new CompletableFuture<>();
    private final StringBuilder receivedText = new StringBuilder();
    private final long deadline = System.nanoTime() + TIMEOUT_NANOS;
    private StreamingHandle handle;
    private boolean closed;
    private ScheduledFuture<?> deadlineTask;

    ModelResponseStream(Consumer<String> consumer, AgentRunControl control, ScheduledExecutorService scheduler) {
        this.consumer = consumer;
        this.control = control;
        deadlineTask = scheduler.scheduleAtFixedRate(this::checkActive, 0, 250, TimeUnit.MILLISECONDS);
        completion.whenComplete((response, error) -> close());
    }

    @Override
    public synchronized void onPartialResponse(String token) {
        if (closed || completion.isDone()) {
            return;
        }
        try {
            receivedText.append(token);
            if (control != null) {
                control.ensureActive();
            }
            consumer.accept(token);
        } catch (RuntimeException exception) {
            completion.completeExceptionally(exception);
        }
    }

    @Override
    public synchronized void onPartialResponse(PartialResponse response, PartialResponseContext context) {
        rememberHandle(context.streamingHandle());
        onPartialResponse(response.text());
    }

    @Override
    public synchronized void onPartialThinking(PartialThinking thinking, PartialThinkingContext context) {
        rememberHandle(context.streamingHandle());
    }

    @Override
    public synchronized void onPartialToolCall(PartialToolCall toolCall, PartialToolCallContext context) {
        rememberHandle(context.streamingHandle());
    }

    @Override
    public synchronized void onCompleteResponse(ChatResponse response) {
        if (!closed) {
            completion.complete(response);
        }
    }

    @Override
    public synchronized void onError(Throwable error) {
        if (!closed) {
            completion.completeExceptionally(error);
        }
    }

    private void checkActive() {
        if (completion.isDone()) {
            return;
        }
        try {
            if (control != null) {
                control.ensureActive();
            }
            if (System.nanoTime() >= deadline) {
                throw new TimeoutException("模型流未在期限内完成");
            }
        } catch (Exception exception) {
            onError(exception);
        }
    }

    CompletableFuture<ChatResponse> completion() { return completion; }

    synchronized String partialText() {
        return receivedText.toString();
    }

    private void rememberHandle(StreamingHandle streamingHandle) {
        handle = streamingHandle;
        if (closed || completion.isCompletedExceptionally()) {
            cancelTransport();
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (deadlineTask != null) {
            deadlineTask.cancel(false);
        }
        if (handle != null && (!completion.isDone() || completion.isCompletedExceptionally())) {
            cancelTransport();
        }
    }

    private void cancelTransport() {
        if (handle == null) {
            return;
        }
        try {
            handle.cancel();
        } catch (RuntimeException exception) {
            // 取消是尽力操作，厂商关闭异常不能覆盖原始超时、预算耗尽或用户取消原因。
            LOGGER.debug("关闭模型流失败: {}", exception.getClass().getSimpleName());
        }
    }
}
