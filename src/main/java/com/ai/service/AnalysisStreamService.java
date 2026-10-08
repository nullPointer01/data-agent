package com.ai.service;

import com.ai.agent.DataAnalysisAgent;
import com.ai.agent.react.ReActStreamEventWriter;
import com.ai.agent.runtime.AgentRunRegistry;
import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentEventSink;
import com.ai.agent.runtime.event.AgentEventType;
import com.ai.agent.runtime.event.AgentSseEventWriter;
import com.ai.config.TaskContextSnapshot;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 用 Flux 表达一次 HTTP SSE 生命周期。订阅才启动 Run，取消订阅会取消执行。
 * MVC 负责订阅、SSE 编码和网络写入，本类只桥接业务事件，不手动创建 SseEmitter。
 */
@Service
public class AnalysisStreamService {
    private static final int MAX_BUFFERED_EVENTS = 256;
    private final DataAnalysisAgent agent;
    private final AgentSseEventWriter eventWriter;
    private final ReActStreamEventWriter legacyWriter;
    private final AgentRunRegistry registry;
    private final AsyncTaskExecutor executor;
    private final Duration timeout;

    public AnalysisStreamService(DataAnalysisAgent agent, AgentSseEventWriter eventWriter,
            ReActStreamEventWriter legacyWriter, AgentRunRegistry registry,
            @Qualifier("sseExecutor") AsyncTaskExecutor executor,
            @Value("${app.sse.timeout-ms:600000}") long timeoutMs) {
        this.agent = agent;
        this.eventWriter = eventWriter;
        this.legacyWriter = legacyWriter;
        this.registry = registry;
        this.executor = executor;
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("SSE 超时必须为正数");
        }
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    /** 返回冷流：控制器调用本方法不执行模型；由 Spring 订阅后开始产生事件。 */
    public Flux<ServerSentEvent<String>> stream(AnalysisRequest request) {
        // 身份必须在 HTTP 请求线程捕获，不能等到 Reactor/线程池执行时再读取。
        TaskContextSnapshot owner = TaskContextSnapshot.capture();
        return Flux.defer(() -> {
            var subscription = new RunSubscription(request, owner);
            return Flux.<ServerSentEvent<String>>create(subscription::start, FluxSink.OverflowStrategy.ERROR)
                    // 模型不会因浏览器变慢而停止生成；缓冲到上限就取消，不能无限堆积。
                    .onBackpressureBuffer(MAX_BUFFERED_EVENTS)
                    // 绝对连接时限，不用“每个 token 重新计时”的 inactivity timeout。
                    .takeUntilOther(Mono.delay(timeout)
                            .flatMap(ignored -> Mono.error(new TimeoutException("SSE connection deadline exceeded"))))
                    .doFinally(signal -> subscription.cancelUnlessTerminal())
                    .onErrorResume(error -> Flux.just(
                            event("{\"type\":\"error\",\"content\":\"流式响应中断或超时，请重试\"}"),
                            event("{\"type\":\"done\"}")));
        });
    }

    private static ServerSentEvent<String> event(String json) {
        return ServerSentEvent.<String>builder(json).event("stream").build();
    }

    /** 每个订阅独立保存取消与终态状态，不把用户之间的运行放进单例字段。 */
    private final class RunSubscription {
        private final AnalysisRequest request;
        private final TaskContextSnapshot owner;
        private final AtomicReference<Future<?>> startup = new AtomicReference<>();
        private final AtomicReference<String> runId = new AtomicReference<>();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private RunSubscription(AnalysisRequest request, TaskContextSnapshot owner) {
            this.request = request;
            this.owner = owner;
        }

        private void start(FluxSink<ServerSentEvent<String>> output) {
            output.onCancel(this::cancelUnlessTerminal);
            if (output.isCancelled()) {
                return;
            }
            try {
                Future<?> future = executor.submit(owner.wrap(() -> execute(output)));
                startup.set(future);
                // 订阅可能在任务提交与 Future 保存之间取消；再次检查，防止孤儿任务。
                if (cancelled.get()) {
                    future.cancel(true);
                }
            } catch (Exception failure) {
                output.error(failure);
            }
        }

        private void execute(FluxSink<ServerSentEvent<String>> output) {
            if (cancelled.get()) {
                return;
            }
            AgentEventSink transport = eventWriter.createSink(json -> output.next(event(json)));
            AgentEventSink tracking = new AgentEventSink() {
                @Override
                public void emit(AgentEvent event) {
                    if (event.type() == AgentEventType.RUN_STARTED) {
                        runId.set(event.runId());
                        if (cancelled.get()) {
                            registry.cancelSystem(event.runId(), "SSE 订阅已取消");
                            return;
                        }
                    }
                    // 暂停/完成已由 Coordinator 固化；消费到最后一个事件后断开不能反向取消审批。
                    if (event.type().isTerminal()) {
                        terminal.set(true);
                    }
                    if (!cancelled.get()) {
                        transport.emit(event);
                    }
                }

                @Override
                public boolean isStreaming() {
                    return true;
                }
            };
            try {
                // Future 负责完整回答保存与终态；事件在此期间直接写入 Flux，不能 join 后再发。
                agent.analyzeStreaming(request, tracking).whenComplete((response, error) -> {
                    if (error != null) {
                        cancelUnlessTerminal();
                        output.error(error);
                    } else {
                        if (response == null || !StringUtils.hasText(response.getRunId())) {
                            rejected(response, output);
                        }
                        output.complete();
                    }
                });
            } catch (Exception failure) {
                cancelUnlessTerminal();
                output.error(failure);
            }
        }

        private void rejected(AnalysisResponse response, FluxSink<ServerSentEvent<String>> output) {
            String error = response != null && StringUtils.hasText(response.getError())
                    ? response.getError() : "分析请求未被接受";
            legacyWriter.emitError(json -> output.next(event(json)), error);
            legacyWriter.emitDone(json -> output.next(event(json)), request == null ? null : request.getSessionId(), null);
            terminal.set(true);
        }

        private void cancelUnlessTerminal() {
            if (terminal.get() || !cancelled.compareAndSet(false, true)) {
                return;
            }
            String id = runId.get();
            if (StringUtils.hasText(id)) {
                registry.cancelSystem(id, "SSE 订阅取消、超时或传输缓冲耗尽");
            }
            Future<?> future = startup.get();
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
    }
}
