package com.ai.agent.durable;

import com.ai.agent.runtime.AgentRunControl;
import com.ai.agent.runtime.AgentRunLimits;
import com.ai.agent.runtime.AgentRunSnapshot;
import org.springframework.stereotype.Component;

/**
 * 在活动 RunControl 与可持久化预算之间建立唯一转换边界。
 *
 * @author data-agent
 */
@Component
public class AgentRunControlFactory {

    public AgentRunBudgetCheckpoint freeze(AgentRunControl control) {
        if (control == null) {
            throw new IllegalArgumentException("Agent Run control 不能为空");
        }
        AgentRunSnapshot snapshot = control.snapshot();
        AgentRunLimits limits = control.getLimits();
        return new AgentRunBudgetCheckpoint(
                limits.timeout().toMillis(),
                limits.maxIterations(),
                limits.maxModelCalls(),
                limits.maxToolCalls(),
                limits.maxTokens(),
                snapshot.iterations(),
                snapshot.modelCalls(),
                snapshot.toolCalls(),
                snapshot.tokens(),
                snapshot.tokenUsageEstimated(),
                snapshot.remainingActiveTimeoutMs());
    }

    public AgentRunControl restore(AgentRunBudgetCheckpoint checkpoint) {
        return AgentRunControl.restore(checkpoint);
    }
}
