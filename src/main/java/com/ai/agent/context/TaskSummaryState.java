package com.ai.agent.context;

import dev.langchain4j.data.message.AiMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** 仅活在根 Run 的有界摘要缓存；只缓存摘要与源指纹，绝不跨用户、会话或进程复用。 */
public final class TaskSummaryState {
    private final List<Snapshot> snapshots = new ArrayList<>();
    private int attempts;
    private boolean generating;
    private final Set<String> attemptedBatches = new HashSet<>();

    synchronized List<Snapshot> snapshots() { return List.copyOf(snapshots); }

    synchronized boolean begin(String batch, int limit) {
        if (generating || attempts >= limit || attemptedBatches.contains(batch)) return false;
        attemptedBatches.add(batch);
        attempts++;
        generating = true;
        return true;
    }

    synchronized void finish(Snapshot snapshot) {
        if (snapshot != null) snapshots.add(snapshot);
        generating = false;
    }

    record Snapshot(String scope, Map<Integer, String> sourceHashes, AiMessage message, long estimatedTokens) {
        Snapshot { sourceHashes = Map.copyOf(sourceHashes); }
        int firstIndex() { return sourceHashes.keySet().stream().mapToInt(Integer::intValue).min().orElseThrow(); }
    }
}
