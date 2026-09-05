package com.ai.agent.runtime.event;

/**
 * 保证终态事件至多发布一次，且终态后拒绝过程事件。
 *
 * @author data-agent
 */
public class GuardedAgentEventSink implements AgentEventSink {

    private final AgentEventSink delegate;
    private boolean terminated;

    public GuardedAgentEventSink(AgentEventSink delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Agent Event delegate 不能为空");
        }
        this.delegate = delegate;
    }

    @Override
    public synchronized void emit(AgentEvent event) {
        if (event == null || terminated) {
            return;
        }
        if (event.type().isTerminal()) {
            terminated = true;
        }
        delegate.emit(event);
    }

    @Override
    public boolean isStreaming() {
        return delegate.isStreaming();
    }

    /**
     * 判断是否已经接受终态事件。
     *
     * @return 是否终止
     */
    public synchronized boolean isTerminated() {
        return terminated;
    }
}
