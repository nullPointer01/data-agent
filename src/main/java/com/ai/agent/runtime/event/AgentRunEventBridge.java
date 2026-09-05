package com.ai.agent.runtime.event;

import com.ai.agent.runtime.AgentRunContext;

import java.util.function.Consumer;

/**
 * 在旧 ReAct Consumer 合同中携带 Run Context，供异步模型回调发布类型化事件。
 *
 * <p>桥本身不消费已序列化 JSON；它只用于迁移期把上下文交给
 * {@code ReActStreamEventWriter}，避免回调线程依赖 ThreadLocal。</p>
 *
 * @author data-agent
 */
public final class AgentRunEventBridge implements Consumer<String> {

    private final AgentRunContext context;

    public AgentRunEventBridge(AgentRunContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Agent Run Context 不能为空");
        }
        this.context = context;
    }

    /**
     * 返回桥关联的 Run Context。
     *
     * @return Run Context
     */
    public AgentRunContext context() {
        return context;
    }

    @Override
    public void accept(String ignored) {
        // 类型化事件由 ReActStreamEventWriter 直接写入 context.eventSink()。
    }
}
