package com.ai.agent.backgroundrun;

import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentEventSink;
import com.ai.agent.runtime.event.AgentEventType;
import com.ai.agent.runtime.event.AgentSseEventWriter;
import java.util.Map;

/** 流式文本先合并再落库；非文本事件和段结束前强制冲刷，维持原始顺序。 */
public final class PersistedRunEventSink implements AgentEventSink {
    private final BackgroundRunStore store;
    private final AgentSseEventWriter writer;
    private final String runId;
    private final String owner;
    private final StringBuilder tokens = new StringBuilder();
    private AgentEvent tokenEvent;
    private long lastFlush = System.nanoTime();

    public PersistedRunEventSink(BackgroundRunStore store, AgentSseEventWriter writer, String runId, String owner) {
        this.store = store; this.writer = writer; this.runId = runId; this.owner = owner;
    }

    @Override public synchronized void emit(AgentEvent event) {
        if (event.type() == AgentEventType.MODEL_TOKEN) {
            tokenEvent = event;
            tokens.append(event.payload().getOrDefault("content", ""));
            if (tokens.length() >= 512 || System.nanoTime() - lastFlush >= 250_000_000L) flush();
        } else {
            flush();
            store.append(runId, owner, writer.encode(event));
        }
    }

    public synchronized void flush() {
        if (tokens.isEmpty()) return;
        store.append(runId, owner, writer.encode(new AgentEvent(runId, tokenEvent.mode(), tokenEvent.occurredAt(),
                AgentEventType.MODEL_TOKEN, Map.of("content", tokens.toString()))));
        tokens.setLength(0);
        lastFlush = System.nanoTime();
    }

    @Override public boolean isStreaming() { return true; }
}
