package com.ai.agent.react;

import com.ai.model.AnalysisRequest;
import com.ai.model.ConversationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.ai.agent.TaskComplexityClassifier;

class ReActFastPathDeciderTest {

    private final ReActFastPathDecider decider = new ReActFastPathDecider(new TaskComplexityClassifier());

    @Test
    void shouldUseFastPathForGreeting() {
        AnalysisRequest request = request("你好，你是谁");

        assertTrue(decider.shouldUseFastPath(request, null, null));
    }

    @Test
    void shouldUseFastPathForSimpleConceptQuestion() {
        AnalysisRequest request = request("什么是向量数据库");

        assertTrue(decider.shouldUseFastPath(request, null, null));
    }

    @Test
    void shouldNotUseFastPathForAnalysisQuestion() {
        AnalysisRequest request = request("分析一下 Q3 销售趋势");

        assertFalse(decider.shouldUseFastPath(request, null, null));
    }

    @Test
    void shouldNotUseFastPathWhenSessionHasHistory() {
        ConversationSession session = new ConversationSession("session-1");
        session.addUserMessage("之前的问题");

        assertFalse(decider.shouldUseFastPath(request("你好"), null, session));
    }

    private AnalysisRequest request(String question) {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion(question);
        return request;
    }
}
