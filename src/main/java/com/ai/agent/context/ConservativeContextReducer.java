package com.ai.agent.context;

import dev.langchain4j.data.message.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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

    private static final String STRATEGY_ID = "PROTOCOL_GROUP_TRIM_V2";

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
        List<ContextMessageGroups.Group> groups = ContextMessageGroups.build(messages, plan);
        Set<Integer> keptIndexes = new LinkedHashSet<>();
        long usedTokens = 0L;

        for (var group : groups) {
            if (group.protectedGroup()) {
                keptIndexes.addAll(group.indexes());
                usedTokens += group.estimatedTokens();
            }
        }

        var optionalNewestFirst = groups.stream()
                .filter(group -> !group.protectedGroup())
                .sorted(Comparator.comparingInt(ContextMessageGroups.Group::lastIndex).reversed())
                .toList();
        for (var group : optionalNewestFirst) {
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

}
