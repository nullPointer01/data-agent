package com.ai.agent.react;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReActRecoveryTrackerTest {

    @Test
    void recordAttemptAllowsWithinBudgetThenStops() {
        ReActRecoveryTracker tracker = new ReActRecoveryTracker();
        ErrorRecoveryAdvice advice = new ErrorRecoveryAdvice(ErrorRecoveryType.SQL_ERROR, "改写 SQL", 2);

        ReActRecoveryDecision first = tracker.recordAttempt(advice);
        ReActRecoveryDecision second = tracker.recordAttempt(advice);
        ReActRecoveryDecision third = tracker.recordAttempt(advice);

        assertTrue(first.allowed());
        assertTrue(second.allowed());
        assertFalse(third.allowed());
    }
}
