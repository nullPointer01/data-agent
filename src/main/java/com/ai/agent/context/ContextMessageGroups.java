package com.ai.agent.context;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 历史对话整轮保留，当前长任务按完整工具轮分组；摘要和裁剪共用同一协议边界。 */
final class ContextMessageGroups {
    private ContextMessageGroups() { }

    static List<Group> build(List<ChatMessage> messages, AgentContextPlan plan) {
        if (messages.size() != plan.messages().size()) throw new IllegalArgumentException("上下文计划不匹配");
        int[] parents = new int[messages.size()];
        for (int i = 0; i < parents.length; i++) parents[i] = i;
        int currentUser = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) { currentUser = i; break; }
        }
        Map<String, Integer> owners = new LinkedHashMap<>();
        int historicalTurn = -1;
        for (int i = 0; i < messages.size(); i++) {
            var message = messages.get(i);
            boolean standalone = message instanceof SystemMessage || plan.messages().get(i).protections()
                    .contains(AgentContextPlan.ProtectionReason.TASK_SUMMARY);
            if (standalone) continue;
            if (message instanceof UserMessage) historicalTurn = i < currentUser ? i : -1;
            // 最新提问单独保护；否则它会把已经完成的每一轮工具都永久绑进 protected 组。
            if (historicalTurn >= 0) union(parents, historicalTurn, i);
            if (message instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                for (var request : ai.toolExecutionRequests()) owners.put(request.id(), i);
            } else if (message instanceof ToolExecutionResultMessage result) {
                Integer owner = owners.get(result.id());
                if (owner != null) union(parents, owner, i);
            }
        }
        Map<Integer, List<Integer>> members = new LinkedHashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            members.computeIfAbsent(find(parents, i), ignored -> new ArrayList<>()).add(i);
        }
        return members.values().stream().map(indexes -> new Group(List.copyOf(indexes), indexes.stream()
                .mapToLong(i -> plan.messages().get(i).estimatedTokens()).sum(), indexes.stream()
                .anyMatch(i -> plan.messages().get(i).protectedMessage()))).toList();
    }

    private static int find(int[] parents, int index) {
        while (parents[index] != index) {
            parents[index] = parents[parents[index]];
            index = parents[index];
        }
        return index;
    }

    private static void union(int[] parents, int left, int right) {
        parents[find(parents, right)] = find(parents, left);
    }

    record Group(List<Integer> indexes, long estimatedTokens, boolean protectedGroup) {
        int lastIndex() { return indexes.get(indexes.size() - 1); }
    }
}
