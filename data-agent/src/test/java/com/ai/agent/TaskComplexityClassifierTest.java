package com.ai.agent;

import com.ai.model.AnalysisRequest;
import com.ai.model.ConversationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskComplexityClassifierTest {

    private final TaskComplexityClassifier classifier = new TaskComplexityClassifier();

    @Test
    void classifyGreetingAsSimple() {
        TaskClassification classification = classifier.classify(request("你好，你是谁"), null, null);

        assertEquals(TaskComplexity.SIMPLE, classification.complexity());
        assertTrue(classification.fastPathAllowed());
    }

    @Test
    void classifyPureConceptQuestionAsSimple() {
        TaskClassification classification = classifier.classify(request("什么是向量数据库"), null, null);

        assertEquals(TaskComplexity.SIMPLE, classification.complexity());
        assertTrue(classification.fastPathAllowed());
    }

    @Test
    void classifyFileRequestAsToolAssisted() {
        AnalysisRequest request = request("分析这个文件");
        request.setFileId("file-1");

        TaskClassification classification = classifier.classify(request, null, null);

        assertEquals(TaskComplexity.TOOL_ASSISTED, classification.complexity());
        assertFalse(classification.fastPathAllowed());
    }

    @Test
    void classifyHistoricalSessionAsComplex() {
        ConversationSession session = new ConversationSession("session-1");
        session.addUserMessage("之前的问题");

        TaskClassification classification = classifier.classify(request("你好"), null, session);

        assertEquals(TaskComplexity.COMPLEX, classification.complexity());
        assertFalse(classification.fastPathAllowed());
    }

    @Test
    void classifyBusinessAnalysisAsToolAssisted() {
        TaskClassification classification = classifier.classify(request("分析一下 Q3 销售趋势"), null, null);

        assertEquals(TaskComplexity.TOOL_ASSISTED, classification.complexity());
        assertFalse(classification.fastPathAllowed());
    }

    private AnalysisRequest request(String question) {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion(question);
        return request;
    }
}
