package com.ai.agent.context;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 保守上下文裁剪：protected 消息优先，其他消息按时间从近到远填充。
 *
 * <p>一次 assistant tool call 与它的 tool result 组成原子组，避免裁剪出无效协议序列。</p>
 *
 * @author data-agent
 */
@Component
public class ConservativeContextReducer implements ContextReductionStrategy {

    private static final String STRATEGY_ID = "CONSERVATIVE_TRIM_V1";

    @Override
    public String strategyId() {
        return STRATEGY_ID;
    }

    @Override
    public ReductionResult reduce(List<ChatMessage> messages, AgentContextPlan plan) {
        if (messages == null || messages.isEmpty()) {
            return new ReductionResult(List.of(), 0L);
        }
        if (plan == null || plan.messages().size() != messages.size()) {
            throw new IllegalArgumentException("上下文计划与消息序列不匹配");
        }

        long messageBudget = Math.max(0L, plan.inputTokenLimit() - plan.estimatedToolTokens());
        List<MessageGroup> groups = buildProtocolGroups(messages, plan);
        Set<Integer> keptIndexes = new LinkedHashSet<>();
        long usedTokens = 0L;

        for (MessageGroup group : groups) {
            if (group.protectedGroup()) {
                keptIndexes.addAll(group.indexes());
                usedTokens += group.estimatedTokens();
            }
        }

        List<MessageGroup> optionalNewestFirst = groups.stream()
                .filter(group -> !group.protectedGroup())
                .sorted(Comparator.comparingInt(MessageGroup::lastIndex).reversed())
                .toList();
        for (MessageGroup group : optionalNewestFirst) {
            if (usedTokens + group.estimatedTokens() > messageBudget) {
                continue;
            }
            keptIndexes.addAll(group.indexes());
            usedTokens += group.estimatedTokens();
        }

        List<ChatMessage> retained = new ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            if (keptIndexes.contains(index)) {
                retained.add(messages.get(index));
            }
        }
        return new ReductionResult(retained, usedTokens);
    }

    private List<MessageGroup> buildProtocolGroups(List<ChatMessage> messages, AgentContextPlan plan) {
        Map<Integer, Set<Integer>> linkedIndexes = new HashMap<>();
        Map<String, Integer> toolCallOwners = new HashMap<>();
        for (int index = 0; index < messages.size(); index++) {
            ChatMessage message = messages.get(index);
            linkedIndexes.computeIfAbsent(index, ignored -> new HashSet<>()).add(index);
            if (message instanceof AiMessage assistant && assistant.hasToolExecutionRequests()) {
                int finalIndex = index;
                assistant.toolExecutionRequests().forEach(request -> toolCallOwners.put(request.id(), finalIndex));
            } else if (message instanceof ToolExecutionResultMessage result) {
                Integer ownerIndex = toolCallOwners.get(result.id());
                if (ownerIndex != null) {
                    linkedIndexes.computeIfAbsent(ownerIndex, ignored -> new HashSet<>()).add(index);
                    linkedIndexes.computeIfAbsent(index, ignored -> new HashSet<>()).add(ownerIndex);
                }
            }
        }

        Set<Integer> visited = new HashSet<>();
        List<MessageGroup> groups = new ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            if (!visited.add(index)) {
                continue;
            }
            Set<Integer> indexes = new HashSet<>();
            collectLinked(index, linkedIndexes, indexes);
            visited.addAll(indexes);
            List<Integer> orderedIndexes = indexes.stream().sorted().toList();
            long tokens = orderedIndexes.stream()
                    .mapToLong(messageIndex -> plan.messages().get(messageIndex).estimatedTokens())
                    .sum();
            boolean protectedGroup = orderedIndexes.stream()
                    .anyMatch(messageIndex -> plan.messages().get(messageIndex).protectedMessage());
            groups.add(new MessageGroup(orderedIndexes, tokens, protectedGroup));
        }
        return groups;
    }

    private void collectLinked(int index, Map<Integer, Set<Integer>> linkedIndexes, Set<Integer> collected) {
        if (!collected.add(index)) {
            return;
        }
        for (Integer linked : linkedIndexes.getOrDefault(index, Set.of())) {
            collectLinked(linked, linkedIndexes, collected);
        }
    }

    private record MessageGroup(List<Integer> indexes, long estimatedTokens, boolean protectedGroup) {

        int lastIndex() {
            return indexes.get(indexes.size() - 1);
        }
    }
}
