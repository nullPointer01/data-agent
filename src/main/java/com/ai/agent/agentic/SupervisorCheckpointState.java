package com.ai.agent.agentic;

import com.ai.agent.capability.AgentCapabilityBindingSnapshot;
import com.ai.agent.capability.AgentCapabilityScope;
import com.ai.agent.capability.AgentDelegationGuard.DelegationRejectedException;
import com.ai.agent.durable.AgentCheckpointMessageMapper;
import com.ai.agent.runtime.planning.AgentToolChoice;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 单次串行 Supervisor 的共享现场，随任务快照进入工具线程，不作为全局缓存。 */
public final class SupervisorCheckpointState {
    private static final String COMPLETED = "harnessCompletedTasks";
    private final AgentCheckpointMessageMapper mapper;
    private final List<ChatMessage> original;
    private final String parentId;
    private final String modelId;
    private final AgentToolChoice choice;
    private final int toolsBefore;
    private final Set<String> parentTools;
    private final Set<String> parentInitialTools;
    private final AgentCapabilityBindingSnapshot parentCapabilities;
    private final Map<String, String> nodes;
    private final List<Map<String, String>> completed = new ArrayList<>();
    private final MessageWindowChatMemory decisions = MessageWindowChatMemory.builder()
            .maxMessages(Integer.MAX_VALUE).alwaysKeepSystemMessageFirst(true).build();
    private DefaultAgenticScope scope;
    private String pendingNode;
    private String pendingTask;

    /** 输入均来自服务端已经解析的执行计划与能力快照。 */
    public SupervisorCheckpointState(AgentCheckpointMessageMapper mapper, List<ChatMessage> original,
            String parentId, String modelId, AgentToolChoice choice, int toolsBefore, Set<String> parentTools, Set<String> parentInitialTools,
            AgentCapabilityBindingSnapshot capabilities, Map<String, String> nodes) {
        this.mapper = mapper;
        this.original = List.copyOf(original);
        this.parentId = parentId;
        this.modelId = modelId;
        this.choice = choice;
        this.toolsBefore = toolsBefore;
        this.parentTools = Set.copyOf(parentTools);
        this.parentInitialTools = Set.copyOf(parentInitialTools);
        this.parentCapabilities = capabilities;
        this.nodes = new LinkedHashMap<>(nodes);
        original.subList(1, original.size() - 1).forEach(decisions::add);
    }

    /** 仅恢复应用自己的完成账本；框架内部执行状态不跨新决策段导入。 */
    public void restore(SupervisorCheckpoint checkpoint) {
        DefaultAgenticScope saved = AgenticScopeSerializer.fromJson(checkpoint.scopeJson());
        scope = saved;
        Object entries = saved.readState(COMPLETED);
        if (!(entries instanceof List<?> list) || list.size() > 256) {
            throw new IllegalArgumentException("Supervisor 已完成节点账本无效");
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> values) || values.size() != 4
                    || !(values.get("node") instanceof String node) || !nodes.containsKey(node)
                    || !(values.get("agentId") instanceof String id) || !nodes.get(node).equals(id)
                    || !(values.get("task") instanceof String task) || task.isBlank()
                    || !(values.get("result") instanceof String result) || result.isBlank()) {
                throw new IllegalArgumentException("Supervisor 完成记录与原节点目录不一致");
            }
            completed.add(Map.of("node", node, "agentId", id, "task", task, "result", result));
        }
        decisions.clear();
        mapper.restore(checkpoint.decisionMessages()).forEach(decisions::add);
        pendingNode = checkpoint.pendingNode();
        pendingTask = checkpoint.pendingTask();
    }

    /** 官方 beforeCall 挂接新决策段，恢复完成事实供框架节点共享。 */
    public void attach(AgenticScope current) {
        if (!(current instanceof DefaultAgenticScope actual)) {
            throw new IllegalStateException("当前框架不支持 AgenticScope 序列化");
        }
        scope = actual;
        writeCompleted();
    }

    /** 相同节点和相同任务已经完成时直接复用事实结果，不重放写操作。 */
    public String completedResult(String node, String task) {
        return completed.stream().filter(item -> node.equals(item.get("node")) && task.equals(item.get("task")))
                .map(item -> item.get("result")).findFirst().orElse(null);
    }

    /** 节点身份由固定目录决定，模型只能提供任务文本。 */
    public void begin(String node, String task) {
        if (!nodes.containsKey(node) || task == null || task.isBlank() || task.length() > 12_000) {
            throw new IllegalArgumentException("Supervisor 待执行节点无效");
        }
        pendingNode = node;
        pendingTask = task;
    }

    /** 完成的节点先记事实，再允许 Supervisor 进行下一次决策。 */
    public void complete(String result) {
        if (pendingNode == null || result == null || result.isBlank() || completed.size() >= 256) {
            throw new IllegalStateException("Supervisor 节点结果或完成账本无效");
        }
        completed.add(Map.of("node", pendingNode, "agentId", nodes.get(pendingNode),
                "task", pendingTask, "result", result));
        writeCompleted();
        pendingNode = null;
        pendingTask = null;
    }

    /** 暂停前只接受当前直接节点；更深层委派缺少父工具对话现场，明确拒绝。 */
    public SupervisorCheckpoint capture(String executorId) {
        var leaf = AgentCapabilityScope.current().orElseThrow();
        if (scope == null || pendingNode == null
                || !executorId.equals("profile:" + nodes.get(pendingNode))
                || !leaf.agentId().equals(nodes.get(pendingNode))) {
            throw new DelegationRejectedException("SUPERVISOR_NESTED_APPROVAL_UNSUPPORTED",
                    "当前嵌套委派缺少完整父级现场，审批动作未执行；请让直接专家或主助手执行该动作");
        }
        writeCompleted();
        return new SupervisorCheckpoint(parentId, modelId, choice, toolsBefore, nodes, parentTools,
                parentCapabilities, leaf, pendingNode, pendingTask, AgenticScopeSerializer.toJson(scope),
                mapper.capture(original), mapper.capture(decisions.messages()), parentInitialTools);
    }

    private void writeCompleted() {
        if (scope != null) scope.writeState(COMPLETED, new ArrayList<>(completed));
    }

    /** 决策历史保持真实消息角色，继续经过统一 Token 治理。 */
    public MessageWindowChatMemory decisions() { return decisions; }

    /** 汇总只读已完成事实，不包含待审批工具的虚构结果。 */
    public List<Map<String, String>> evidence() { return List.copyOf(completed); }
}
