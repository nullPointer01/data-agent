package com.ai.agent.agentic;

import com.ai.agent.capability.AgentCapabilityBindingSnapshot;
import com.ai.agent.durable.AgentCheckpointMessage;
import com.ai.agent.runtime.planning.AgentToolChoice;
import com.ai.agent.runtime.AgentExecutionMode;
import com.ai.agent.tool.AgentTools;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Supervisor 在节点审批边界的现场；不把框架内部 Planner 游标当作应用恢复协议。 */
public record SupervisorCheckpoint(String parentAgentId, String modelId, AgentToolChoice initialChoice,
        int toolCallsBefore, Map<String, String> nodes, Set<String> parentTools,
        AgentCapabilityBindingSnapshot parentCapabilities, AgentCapabilityBindingSnapshot leafCapabilities,
        String pendingNode, String pendingTask, String scopeJson,
        List<AgentCheckpointMessage> originalMessages, List<AgentCheckpointMessage> decisionMessages,
        Set<String> parentInitialTools) {

    public SupervisorCheckpoint {
        if (parentAgentId == null || parentAgentId.isBlank() || initialChoice == null || toolCallsBefore < 0
                || parentCapabilities == null || leafCapabilities == null || nodes == null
                || nodes.isEmpty() || nodes.size() > 256 || pendingNode == null || !nodes.containsKey(pendingNode)
                || pendingTask == null || pendingTask.isBlank() || pendingTask.length() > 12_000
                || scopeJson == null || scopeJson.isBlank() || scopeJson.length() > 1_048_576
                || originalMessages == null || originalMessages.size() < 2 || decisionMessages == null) {
            throw new IllegalArgumentException("Supervisor 检查点缺少完整节点现场或超过上限");
        }
        if (!parentAgentId.equals(parentCapabilities.agentId())
                || !nodes.get(pendingNode).equals(leafCapabilities.agentId())
                || !parentAgentId.equals(nodes.get("local_assistant"))
                || parentCapabilities.mode() != AgentExecutionMode.ORCHESTRATED
                || parentTools == null || !parentTools.contains(AgentTools.DELEGATE_TO_AGENT_TOOL)
                || !parentCapabilities.callableToolNames().containsAll(parentTools)
                || originalMessages.get(0).role() != AgentCheckpointMessage.Role.SYSTEM
                || originalMessages.get(originalMessages.size() - 1).role() != AgentCheckpointMessage.Role.USER) {
            throw new IllegalArgumentException("Supervisor 节点身份与能力现场不一致");
        }
        modelId = modelId == null ? "" : modelId;
        nodes = Map.copyOf(nodes);
        parentTools = Set.copyOf(parentTools);
        parentInitialTools = parentInitialTools == null ? parentTools : Set.copyOf(parentInitialTools);
        if (!parentTools.containsAll(parentInitialTools)) throw new IllegalArgumentException("Supervisor 初始工具超出原授权池");
        originalMessages = List.copyOf(originalMessages);
        decisionMessages = List.copyOf(decisionMessages);
    }
}
