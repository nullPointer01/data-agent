package com.ai.agent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentUtilityToolServiceTest {

    @Test
    void calculateEvaluatesArithmeticExpression() {
        AgentUtilityToolService service = new AgentUtilityToolService();

        String result = service.calculate("(100+200)*0.8");

        assertEquals("(100+200)*0.8 = 240", result);
    }

    @Test
    void calculateHandlesPercentNumber() {
        AgentUtilityToolService service = new AgentUtilityToolService();

        String result = service.calculate("200*50%");

        assertEquals("200*50% = 100", result);
    }

    @Test
    void calculateRejectsEmptyExpressionAfterSanitize() {
        AgentUtilityToolService service = new AgentUtilityToolService();

        String result = service.calculate("hello");

        assertEquals("无效的数学表达式", result);
    }

    @Test
    void getCurrentTimeReturnsFormattedText() {
        AgentUtilityToolService service = new AgentUtilityToolService();

        String result = service.getCurrentTime();

        assertTrue(result.startsWith("当前时间: "));
    }
}
