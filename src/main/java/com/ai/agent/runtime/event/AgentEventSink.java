package com.ai.agent.runtime.event;

/**
 * Agent Run 过程事件的统一接收端。
 *
 * @author data-agent
 */
@FunctionalInterface
public interface AgentEventSink {

    /**
     * 发布一个类型化事件。
     *
     * @param event Agent 事件
     */
    void emit(AgentEvent event);

    /**
     * 判断接收端是否需要实时流式输出。
     *
     * @return 是否为流式接收端
     */
    default boolean isStreaming() {
        return false;
    }

    /**
     * 返回忽略所有事件的同步接收端。
     *
     * @return 空事件接收端
     */
    static AgentEventSink noop() {
        return event -> {
        };
    }
}
