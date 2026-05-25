package com.ai.agent.react;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.ai.agent.AgentReasoningProperties;

class ErrorRecoveryAdvisorTest {

    private final ErrorRecoveryAdvisor advisor = new ErrorRecoveryAdvisor(new AgentReasoningProperties());

    @Test
    void adviseUnknownTool() {
        ErrorRecoveryAdvice advice = advisor.advise("missingTool", "未知工具: missingTool");

        assertEquals(ErrorRecoveryType.UNKNOWN_TOOL, advice.type());
        assertTrue(advice.hasInstruction());
        assertEquals(1, advice.maxAttempts());
    }

    @Test
    void adviseSqlFailureBeforeGenericFailure() {
        ErrorRecoveryAdvice advice = advisor.advise("executeSQL", "工具执行失败: SQL syntax error");

        assertEquals(ErrorRecoveryType.SQL_ERROR, advice.type());
        assertTrue(advice.instruction().contains("Schema"));
        assertEquals(2, advice.maxAttempts());
    }

    @Test
    void appendAdviceKeepsNormalResultClean() {
        String result = advisor.appendAdvice("calculate", "2");

        assertEquals("2", result);
    }

    @Test
    void appendAdviceAddsRecoverySectionForNoData() {
        String result = advisor.appendAdvice("listFiles", "没有已上传的文件");

        assertTrue(result.contains("错误恢复建议"));
        assertFalse(result.equals("没有已上传的文件"));
    }
}
