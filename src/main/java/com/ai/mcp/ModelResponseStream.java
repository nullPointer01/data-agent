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
 * SDK 流的异步完成句柄；共享定时器检查预算/取消，终态后仅接收迟到用量校正。
 */
final class ModelResponseStream implements StreamingChatResponseHandler, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelResponseStream.class);
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(300);
    private final Consumer<String> consumer;
    private final AgentRunControl control;
    private final Consumer<ChatResponse> lateResponse;
    private final CompletableFuture<ChatResponse> completion = new CompletableFuture<>();
    private final long deadline = System.nanoTime() + TIMEOUT_NANOS;
    private StreamingHandle handle;
    private boolean closed;
    private ScheduledFuture<?> deadlineTask;

    ModelResponseStream(Consumer<String> consumer, AgentRunControl control, ScheduledExecutorService scheduler,
            Consumer<ChatResponse> lateResponse) {
        this.consumer = consumer;
        this.control = control;
        this.lateResponse = lateResponse;
        synchronized (this) {
            // 首次检查可立即结束已取消的 Run；先持有锁，避免 close 早于定时器赋值导致泄漏。
            deadlineTask = scheduler.scheduleAtFixedRate(this::checkActive, 0, 250, TimeUnit.MILLISECONDS);
            completion.whenComplete((response, error) -> close());
        }
    }

    @Override
    public synchronized void onPartialResponse(String token) {
        if (closed || completion.isDone()) {
            return;
        }
        try {
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
        } else if (completion.isCompletedExceptionally()) {
            // 取消传输不能保证厂商停止；迟到用量只校正账本，绝不重新发布回答或执行工具。
            lateResponse.accept(response);
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

    /** 预占数据库等待期间也可能被取消；发送前重验，不能让已关闭的流继续发起请求。 */
    synchronized void ensureOpen() {
        if (closed || completion.isDone()) throw new java.util.concurrent.CancellationException("模型流已经关闭");
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
