package com.ai.agent.runtime;

import com.ai.agent.durable.AgentRunBudgetCheckpoint;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 管理一次 Agent Run 的状态、截止时间和原子预算。
 *
 * @author data-agent
 */
public class AgentRunControl {

    private static final String DEFAULT_CANCEL_DETAIL = "运行已取消";
    private static final String DEFAULT_FAILURE_DETAIL = "运行失败";

    private final AgentRunLimits limits;
    private final Clock clock;
    private final Instant startedAt;
    private final Instant deadline;
    private final long activeDurationBeforeMs;
    private final AtomicReference<LifecycleState> lifecycle = new AtomicReference<>(
            new LifecycleState(AgentRunStatus.CREATED, AgentRunTerminationReason.NONE, "", null, null));
    private final AtomicInteger iterations = new AtomicInteger();
    private final AtomicInteger modelCalls = new AtomicInteger();
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final AtomicLong tokens = new AtomicLong();
    private final AtomicBoolean tokenUsageEstimated = new AtomicBoolean();

    public AgentRunControl(AgentRunLimits limits) {
        this(limits, Clock.systemUTC());
    }

    AgentRunControl(AgentRunLimits limits, Clock clock) {
        this(limits, clock, null);
    }

    private AgentRunControl(AgentRunLimits limits, Clock clock, AgentRunBudgetCheckpoint restored) {
        if (limits == null) {
            throw new IllegalArgumentException("Agent Run limits 不能为空");
        }
        this.limits = limits;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.startedAt = this.clock.instant();
        Duration activeTimeout = restored == null
                ? limits.timeout()
                : Duration.ofMillis(restored.remainingActiveTimeoutMs());
        this.deadline = startedAt.plus(activeTimeout);
        this.activeDurationBeforeMs = restored == null
                ? 0L
                : Math.max(0L, restored.timeoutMs() - restored.remainingActiveTimeoutMs());
        if (restored != null) {
            iterations.set(restored.usedIterations());
            modelCalls.set(restored.usedModelCalls());
            toolCalls.set(restored.usedToolCalls());
            tokens.set(restored.usedTokens());
            tokenUsageEstimated.set(restored.tokenUsageEstimated());
            lifecycle.set(new LifecycleState(
                    AgentRunStatus.RUNNING,
                    AgentRunTerminationReason.NONE,
                    "",
                    null,
                    null));
        }
    }

    /**
     * 从受校验的冻结预算恢复活动控制器。
     *
     * @param checkpoint 冻结预算
     * @return 已处于 RUNNING 的控制器
     */
    public static AgentRunControl restore(AgentRunBudgetCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("Agent Run budget checkpoint 不能为空");
        }
        return new AgentRunControl(checkpoint.toLimits(), Clock.systemUTC(), checkpoint);
    }

    /**
     * 将新建运行切换为运行中。
     *
     * @return 是否成功启动
     */
    public boolean start() {
        LifecycleState current = lifecycle.get();
        if (current.status() != AgentRunStatus.CREATED) {
            return false;
        }
        LifecycleState running = new LifecycleState(
                AgentRunStatus.RUNNING,
                AgentRunTerminationReason.NONE,
                "",
                null,
                null);
        return lifecycle.compareAndSet(current, running);
    }

    /**
     * 冻结当前 active timeout 和预算，进入等待审批状态。
     *
     * @param detail 安全等待说明
     * @return 是否成功暂停
     */
    public boolean suspendForApproval(String detail) {
        while (true) {
            LifecycleState current = lifecycle.get();
            if (current.status() != AgentRunStatus.RUNNING) {
                return false;
            }
            Instant now = clock.instant();
            Duration remaining = Duration.between(now, deadline);
            if (remaining.isZero() || remaining.isNegative()) {
                terminate(AgentRunStatus.TIMED_OUT, AgentRunTerminationReason.DEADLINE_EXCEEDED,
                        "运行已超过截止时间");
                return false;
            }
            LifecycleState waiting = new LifecycleState(
                    AgentRunStatus.WAITING_APPROVAL,
                    AgentRunTerminationReason.NONE,
                    normalizeDetail(detail, "等待人工审批"),
                    now,
                    remaining);
            if (lifecycle.compareAndSet(current, waiting)) {
                return true;
            }
        }
    }

    /**
     * 为一次推理迭代申请预算。
     */
    public void beforeIteration() {
        ensureActive();
        acquire(iterations, limits.maxIterations(), AgentRunTerminationReason.ITERATION_LIMIT,
                "已达到最大推理迭代数");
    }

    /**
     * 为一次业务模型调用申请预算。
     */
    public void beforeModelCall() {
        ensureActive();
        if (tokens.get() >= limits.maxTokens()) {
            terminate(AgentRunStatus.BUDGET_EXHAUSTED, AgentRunTerminationReason.TOKEN_LIMIT,
                    "已达到最大 Token 预算");
            throw terminated();
        }
        acquire(modelCalls, limits.maxModelCalls(), AgentRunTerminationReason.MODEL_CALL_LIMIT,
                "已达到最大模型调用数");
    }

    /**
     * 结算一次模型调用的 Token 用量。
     *
     * @param usedTokens 实际或估算用量
     * @param estimated 是否为估算值
     */
    public void afterModelCall(long usedTokens, boolean estimated) {
        if (usedTokens <= 0) {
            return;
        }
        long total = tokens.addAndGet(usedTokens);
        if (estimated) {
            tokenUsageEstimated.set(true);
        }
        if (total >= limits.maxTokens()) {
            terminate(AgentRunStatus.BUDGET_EXHAUSTED, AgentRunTerminationReason.TOKEN_LIMIT,
                    "已达到最大 Token 预算");
        }
    }

    /**
     * 为一次工具实现调用申请预算。
     */
    public void beforeToolCall() {
        ensureActive();
        acquire(toolCalls, limits.maxToolCalls(), AgentRunTerminationReason.TOOL_CALL_LIMIT,
                "已达到最大工具调用数");
    }

    /**
     * 检查运行是否仍允许继续执行。
     */
    public void ensureActive() {
        if (lifecycle.get().status() != AgentRunStatus.RUNNING) {
            throw terminated();
        }
        if (Thread.currentThread().isInterrupted()) {
            terminate(AgentRunStatus.CANCELLED, AgentRunTerminationReason.INTERRUPTED, "运行线程已中断");
            throw terminated();
        }
        if (!clock.instant().isBefore(deadline)) {
            terminate(AgentRunStatus.TIMED_OUT, AgentRunTerminationReason.DEADLINE_EXCEEDED, "运行已超过截止时间");
            throw terminated();
        }
    }

    /**
     * 将活动运行标记为成功。
     *
     * @return 是否赢得终态竞争
     */
    public boolean complete() {
        if (Thread.currentThread().isInterrupted()) {
            terminate(AgentRunStatus.CANCELLED, AgentRunTerminationReason.INTERRUPTED, "运行线程已中断");
            return false;
        }
        if (!clock.instant().isBefore(deadline)) {
            terminate(AgentRunStatus.TIMED_OUT, AgentRunTerminationReason.DEADLINE_EXCEEDED, "运行已超过截止时间");
            return false;
        }
        return terminate(AgentRunStatus.COMPLETED, AgentRunTerminationReason.COMPLETED, "运行完成");
    }

    /**
     * 将活动运行标记为失败。
     *
     * @param failureDetail 安全错误说明
     * @return 是否赢得终态竞争
     */
    public boolean fail(String failureDetail) {
        return terminate(AgentRunStatus.FAILED, AgentRunTerminationReason.FAILED,
                normalizeDetail(failureDetail, DEFAULT_FAILURE_DETAIL));
    }

    /**
     * 当审批持久化失败时，将已经暂停的内存控制器收敛为失败终态。
     *
     * @param failureDetail 安全错误说明
     * @return 是否成功收敛
     */
    public boolean failSuspension(String failureDetail) {
        return terminateFrom(
                AgentRunStatus.WAITING_APPROVAL,
                AgentRunStatus.FAILED,
                AgentRunTerminationReason.FAILED,
                normalizeDetail(failureDetail, DEFAULT_FAILURE_DETAIL));
    }

    /**
     * 协作式取消活动运行。
     *
     * @param cancelDetail 取消说明
     * @return 是否赢得终态竞争
     */
    public boolean cancel(String cancelDetail) {
        return terminate(AgentRunStatus.CANCELLED, AgentRunTerminationReason.CANCELLED,
                normalizeDetail(cancelDetail, DEFAULT_CANCEL_DETAIL));
    }

    /**
     * 读取当前状态快照。
     *
     * @return 运行快照
     */
    public AgentRunSnapshot snapshot() {
        LifecycleState current = lifecycle.get();
        Instant end = current.stoppedAt() == null ? clock.instant() : current.stoppedAt();
        long durationMs = activeDurationBeforeMs + Math.max(0L, Duration.between(startedAt, end).toMillis());
        long usedTokens = tokens.get();
        long remainingActiveTimeoutMs = remainingTime(current).toMillis();
        return new AgentRunSnapshot(
                current.status(),
                current.reason(),
                current.detail(),
                startedAt,
                deadline,
                current.status().isTerminal() ? current.stoppedAt() : null,
                durationMs,
                iterations.get(),
                modelCalls.get(),
                toolCalls.get(),
                usedTokens,
                tokenUsageEstimated.get(),
                Math.max(0L, usedTokens - limits.maxTokens()),
                remainingActiveTimeoutMs);
    }

    public AgentRunLimits getLimits() {
        return limits;
    }

    /**
     * 返回当前 Run 距离截止时间的非负剩余时长。
     *
     * @return 剩余时长
     */
    public Duration remainingTime() {
        return remainingTime(lifecycle.get());
    }

    private Duration remainingTime(LifecycleState state) {
        Duration remaining = state.remainingActiveTimeout() == null
                ? Duration.between(clock.instant(), deadline)
                : state.remainingActiveTimeout();
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private void acquire(AtomicInteger counter, int maximum, AgentRunTerminationReason reason, String message) {
        while (true) {
            ensureActive();
            int current = counter.get();
            if (current >= maximum) {
                terminate(AgentRunStatus.BUDGET_EXHAUSTED, reason, message);
                throw terminated();
            }
            if (counter.compareAndSet(current, current + 1)) {
                return;
            }
        }
    }

    private boolean terminate(AgentRunStatus terminalStatus, AgentRunTerminationReason reason, String message) {
        return terminateFrom(AgentRunStatus.RUNNING, terminalStatus, reason, message);
    }

    private boolean terminateFrom(AgentRunStatus expectedStatus, AgentRunStatus terminalStatus,
            AgentRunTerminationReason reason, String message) {
        if (!terminalStatus.isTerminal()) {
            throw new IllegalArgumentException("目标状态不是 Agent Run 终态: " + terminalStatus);
        }
        while (true) {
            LifecycleState current = lifecycle.get();
            if (current.status() != expectedStatus) {
                return false;
            }
            LifecycleState terminal = new LifecycleState(
                    terminalStatus,
                    reason == null ? AgentRunTerminationReason.NONE : reason,
                    message == null ? "" : message,
                    clock.instant(),
                    Duration.ZERO);
            if (lifecycle.compareAndSet(current, terminal)) {
                return true;
            }
        }
    }

    private AgentRunTerminatedException terminated() {
        return new AgentRunTerminatedException(snapshot());
    }

    private String normalizeDetail(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 保证状态、原因与结束时间作为一个整体原子发布。
     */
    private record LifecycleState(
            AgentRunStatus status,
            AgentRunTerminationReason reason,
            String detail,
            Instant stoppedAt,
            Duration remainingActiveTimeout) {
    }
}
