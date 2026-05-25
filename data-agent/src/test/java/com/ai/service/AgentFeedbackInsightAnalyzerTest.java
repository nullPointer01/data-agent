package com.ai.service;

import com.ai.agent.dto.AgentFeedbackInsightResponse;
import com.ai.model.AgentFeedback;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFeedbackInsightAnalyzerTest {

    private final AgentFeedbackInsightAnalyzer analyzer = new AgentFeedbackInsightAnalyzer(
            new AgentFeedbackInsightProperties(null, null, null, null, null, null));

    @Test
    void analyzeClassifiesNegativeFeedbacks() {
        List<AgentFeedback> feedbacks = List.of(
                feedback("f1", "DOWN", "答案不准确，算错了", "销售额是多少", "销售额为 10"),
                feedback("f2", "DOWN", "知识库没找到引用来源", "制度是什么", "未找到相关资料"),
                feedback("f3", "DOWN", "格式看不懂", "给我图表", "一段纯文本"));

        AgentFeedbackInsightResponse response = analyzer.analyze(feedbacks, 3L);

        assertTrue(response.success());
        assertEquals(3, response.sampleSize());
        assertEquals(3L, response.negativeCount());
        assertEquals("ACCURACY", response.issues().get(0).issueType());
        assertEquals("RAG_GAP", response.issues().get(1).issueType());
        assertEquals("EXPERIENCE", response.issues().get(2).issueType());
        assertEquals(4, response.recommendations().size());
    }

    @Test
    void analyzeAddsOtherIssueForUnmatchedFeedbacks() {
        List<AgentFeedback> feedbacks = List.of(
                feedback("f1", "DOWN", "这次不满意", "帮我分析", "无法满足"));

        AgentFeedbackInsightResponse response = analyzer.analyze(feedbacks, 1L);

        assertEquals(1, response.issues().size());
        assertEquals("OTHER", response.issues().get(0).issueType());
        assertEquals(1D, response.issues().get(0).ratio());
    }

    private AgentFeedback feedback(String feedbackId, String rating, String comment, String question, String answer) {
        AgentFeedback feedback = new AgentFeedback();
        feedback.setFeedbackId(feedbackId);
        feedback.setTenantId("tenant-a");
        feedback.setUserId("user-a");
        feedback.setRating(rating);
        feedback.setComment(comment);
        feedback.setQuestion(question);
        feedback.setAnswer(answer);
        return feedback;
    }
}
