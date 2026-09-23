package com.ai.agent.runtime.planning;

import java.util.List;
import java.util.Optional;

/**
 * 对本地能力介绍以外的请求执行受限语义路由。
 *
 * @author data-agent
 */
public interface SemanticRequestRouter {

    /**
     * 返回经过校验的结构化路由决策；任何不确定或无效结果都返回空。
     *
     * @param input 最小路由输入
     * @return 可安全采用的决策
     */
    Result route(Input input);

    /** 语义决策及本次 Router 调用的规划阶段观测。 */
    record Result(Optional<Decision> decision, boolean modelRequestAttempted, long durationMs) {
        public Result {
            decision = decision == null ? Optional.empty() : decision;
            durationMs = Math.max(0L, durationMs);
        }

        public static Result skipped() {
            return new Result(Optional.empty(), false, 0L);
        }
    }

    /**
     * @param question 当前原始问题
     * @param recentConversation 当前会话的近期文本，只用于指代消解
     * @param agentModelId 当前 Agent 模型编号
     * @param agentDescription Agent 职责说明，不作为权限来源
     * @param filePresent 本次请求是否带有文件
     * @param tools 当前模式下已绑定且授权的工具目录
     */
    record Input(String question, String recentConversation, String agentModelId,
            String agentDescription, boolean filePresent, List<ToolCandidate> tools) {
        public Input {
            question = question == null ? "" : question.trim();
            recentConversation = recentConversation == null ? "" : recentConversation.trim();
            agentModelId = agentModelId == null ? "" : agentModelId.trim();
            agentDescription = agentDescription == null ? "" : agentDescription.trim();
            tools = tools == null ? List.of() : List.copyOf(tools);
        }
    }

    /** 最小工具元数据，不包含连接配置、密钥或执行器。 */
    record ToolCandidate(String name, String description, boolean readOnly,
            List<CapabilitySummary> targets) {
        public ToolCandidate {
            targets = targets == null ? List.of() : List.copyOf(targets);
        }
    }

    /** Skill/子 Agent 的稳定身份和用途，实际执行仍检查绑定。 */
    record CapabilitySummary(String id, String name, String description) {
    }

    /**
     * @param intent 请求目标
     * @param semanticMemory 语义记忆要求
     * @param knowledgeBase 知识库要求
     * @param retrievalQuery 独立检索查询
     * @param confidence 路由置信度
     * @param reason 可展示的简短原因
     * @param candidateTools 从输入目录选择的精确工具名
     */
    record Decision(
            RequestIntent intent,
            ContextRequirement semanticMemory,
            ContextRequirement knowledgeBase,
            String retrievalQuery,
            double confidence,
            String reason,
            List<String> candidateTools) {
        public Decision {
            // 保留重复名称供准入校验拒绝，反序列化时不能被 Set 悄悄去重。
            candidateTools = candidateTools == null ? List.of() : List.copyOf(candidateTools);
        }
    }
}
