package com.ai.rag;
import com.ai.rag.retrieval.RetrievalChannel;

import com.ai.rag.dto.RagCitation;
import com.ai.rag.dto.RagContextResponse;
import com.ai.rag.dto.RagQualityEvaluationRequest;
import com.ai.rag.dto.RagRetrievalTrace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagQualityEvaluationServiceTest {

    private final RagQualityEvaluationService service = new RagQualityEvaluationService(
            new RagProperties(), new RagQueryRewriter());

    @Test
    void evaluatePassesWhenKeywordsCitationsAndChannelsMatch() {
        RagContextResponse response = response("""
                资料 [R1] 销售制度要求客户分层经营。
                资料 [R2] 销售流程包含合同审批。
                """, List.of(
                citation("R1", "policy-1", "chunk-1", List.of(RetrievalChannel.VECTOR, RetrievalChannel.FULL_TEXT)),
                citation("R2", "policy-2", "chunk-2", List.of(RetrievalChannel.VECTOR, RetrievalChannel.FULL_TEXT))));
        RagQualityEvaluationRequest request = new RagQualityEvaluationRequest("销售制度流程",
                response, List.of("销售", "制度", "流程"), List.of("policy-1", "policy-2"));

        var evaluation = service.evaluate(request);

        assertTrue(evaluation.passed());
        assertEquals(1D, evaluation.overallScore());
        assertEquals(1D, evaluation.citationAccuracy());
        assertEquals(2, evaluation.validCitationCount());
    }

    @Test
    void evaluateFailsWhenCitationAndHybridCoverageAreMissing() {
        RagContextResponse response = response("资料 [R1] 客户分层经营。", List.of(
                new RagCitation("R1", "KNOWLEDGE", "", "", 0.1D, null, null, List.of(),
                        "", "", 0, 0, false, false, false, false)));
        response.setTrace(trace(0));
        RagQualityEvaluationRequest request = new RagQualityEvaluationRequest("销售制度流程",
                response, List.of("销售", "制度", "流程"), List.of("policy-1"));

        var evaluation = service.evaluate(request);

        assertFalse(evaluation.passed());
        assertTrue(evaluation.issues().stream().anyMatch(issue -> issue.contains("引用准确率")));
        assertTrue(evaluation.issues().stream().anyMatch(issue -> issue.contains("混合召回覆盖")));
    }

    private RagContextResponse response(String context, List<RagCitation> citations) {
        return new RagContextResponse(context, citations.size(), "销售制度流程", "PROCEDURE",
                List.of("销售", "制度", "流程"), citations, trace(citations.size()));
    }

    private RagCitation citation(String referenceId, String sourceId, String chunkId, List<String> channels) {
        return new RagCitation(referenceId, "KNOWLEDGE", sourceId, chunkId, 0.92D,
                0.88D, 0.79D, channels, "销售制度流程摘要", "制度/销售", 0, 24,
                false, false, true, false);
    }

    private RagRetrievalTrace trace(int count) {
        return new RagRetrievalTrace(true, "jpa", "milvus", 20, 6, 5000, 0.45D,
                count, count, count, count, count, count, count, 80L, 30L, 40L,
                3L, 2L, 1L, 1L, 120L, 300, false);
    }
}
