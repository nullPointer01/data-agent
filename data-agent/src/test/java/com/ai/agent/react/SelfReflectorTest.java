package com.ai.agent.react;

import com.ai.model.AnalysisResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import com.ai.agent.AgentReasoningProperties;

class SelfReflectorTest {

    private final SelfReflector selfReflector = new SelfReflector(new AgentReasoningProperties());

    @Test
    void reflectFormatsRecoveryAdvice() {
        ErrorRecoveryAdvice advice = new ErrorRecoveryAdvice(ErrorRecoveryType.SQL_ERROR, "先查 Schema", 2);
        ReActStepOutcome outcome = ReActStepOutcome.toolObservation("executeSQL", "SQL error", false, advice);

        String reflection = selfReflector.reflect(outcome, 1);

        assertTrue(reflection.contains("executeSQL"));
        assertTrue(reflection.contains("SQL_ERROR"));
        assertTrue(reflection.contains("1/2"));
    }

    @Test
    void reflectFinalAnswerIncludesConfidenceAndRecommendation() {
        String reflection = selfReflector.reflectFinalAnswer("已根据工具结果完成分析", java.util.List.of(
                new AnalysisResponse.ThinkingStep(1, "tool_call", "调用工具")));

        assertTrue(reflection.contains("置信度"));
        assertTrue(reflection.contains("高"));
        assertTrue(reflection.contains("后续建议"));
    }
}
