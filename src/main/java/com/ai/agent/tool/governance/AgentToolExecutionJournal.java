package com.ai.agent.tool.governance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 每个 Run 独占的有界、线程安全工具执行 Journal。
 *
 * @author data-agent
 */
public final class AgentToolExecutionJournal {

    private final int capacity;
    private final Deque<AgentToolExecutionRecord> records;
    private long overflowCount;

    public AgentToolExecutionJournal(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Tool Journal 容量必须为正数");
        }
        this.capacity = capacity;
        this.records = new ArrayDeque<>(capacity);
    }

    public synchronized void append(AgentToolExecutionRecord record) {
        if (record == null) {
            return;
        }
        if (records.size() == capacity) {
            records.removeFirst();
            overflowCount++;
        }
        records.addLast(record);
    }

    public synchronized List<AgentToolExecutionRecord> snapshot() {
        return List.copyOf(new ArrayList<>(records));
    }

    public synchronized long overflowCount() {
        return overflowCount;
    }

    public int capacity() {
        return capacity;
    }
}
