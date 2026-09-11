package com.ai.agent.outcome;

import com.ai.agent.runtime.AgentRunStatus;

import java.util.Set;

/**
 * 使用安全 Run 证据评估任务结果的统一边界。
 *
 * @author data-agent
 */
public interface AgentOutcomeEvaluator {

    /**
     * 对任务合同执行可复现评估。
     *
     * @param taskContract 可选任务合同
     * @param evidence 安全运行证据
     * @return 任务结果评估
     */
    AgentOutcomeEvaluation evaluate(AgentTaskContract taskContract, OutcomeEvidence evidence);

    /**
     * evaluator 可使用的最小安全证据，不包含原始工具参数或隐藏推理。
     *
     * @author data-agent
     */
    record OutcomeEvidence(
            String finalAnswer,
            AgentRunStatus runStatus,
            Set<String> calledTools,
            Set<String> approvalStatuses,
            boolean toolEvidenceComplete,
            boolean approvalEvidenceComplete) {

        public OutcomeEvidence {
            calledTools = calledTools == null ? Set.of() : Set.copyOf(calledTools);
            approvalStatuses = approvalStatuses == null ? Set.of() : Set.copyOf(approvalStatuses);
        }
    }
}
