package com.ai.memory;

import com.ai.mcp.McpModelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SemanticMemoryExtractorTest {

    private McpModelService modelService;
    private SemanticMemoryExtractor extractor;

    @BeforeEach
    void setUp() {
        MemoryProperties properties = new MemoryProperties();
        properties.setSemanticExtractionMinConfidence(0.85D);
        modelService = mock(McpModelService.class);
        extractor = new SemanticMemoryExtractor(
                new MemoryWorthinessEvaluator(), modelService, new ObjectMapper(), properties);
    }

    @Test
    void shouldExtractExplicitCommunicationAndFormatPreferences() {
        List<SemanticMemoryCandidate> candidates = extractor.extractExplicit(
                "请记住：以后回答保持简洁，优先使用表格");

        assertThat(candidates)
                .extracting(SemanticMemoryCandidate::semanticKey, SemanticMemoryCandidate::content)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                SemanticMemoryKey.COMMUNICATION_STYLE, "简洁直接"),
                        org.assertj.core.groups.Tuple.tuple(SemanticMemoryKey.OUTPUT_FORMAT, "表格优先"));
    }

    @Test
    void shouldHonorReplacementInsteadOfNegatedPreference() {
        List<SemanticMemoryCandidate> candidates = extractor.extractExplicit(
                "请记住：以后不要使用表格，改用列表，并且需要详细说明");

        assertThat(candidates)
                .extracting(SemanticMemoryCandidate::semanticKey, SemanticMemoryCandidate::content)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                SemanticMemoryKey.COMMUNICATION_STYLE, "详细解释"),
                        org.assertj.core.groups.Tuple.tuple(SemanticMemoryKey.OUTPUT_FORMAT, "列表优先"));
    }

    @Test
    void shouldNotTreatAssistantSelfDescriptionAsUserRole() {
        List<SemanticMemoryCandidate> candidates = extractor.extractExplicit("请记住：我是你的助手");

        assertThat(candidates)
                .noneMatch(candidate -> SemanticMemoryKey.ROLE.equals(candidate.semanticKey()));
    }

    @Test
    void shouldRejectSensitiveInformationBeforeCallingModel() {
        assertThat(extractor.extractExplicit("请记住：API_KEY=sk-secret-value")).isEmpty();
        assertThat(extractor.extractImplicit("我的密码是 123456", "好的", "model-1")).isEmpty();
        verifyNoInteractions(modelService);
    }

    @Test
    void shouldAcceptOnlyHighConfidenceCandidatesWithVerbatimEvidence() {
        String userMessage = "我通常希望结论放在最前面";
        when(modelService.callBackgroundModelJson(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("model-1")))
                .thenReturn("""
                        {"memories":[
                          {"type":"PREFERENCE","semanticKey":"preference.answer_order","content":"结论优先","confidence":0.91,"evidence":"希望结论放在最前面"},
                          {"type":"ENTITY","semanticKey":"profile.role","content":"产品经理","confidence":0.99,"evidence":"不存在的证据"},
                          {"type":"PREFERENCE","semanticKey":"preference.detail","content":"详细","confidence":0.50,"evidence":"通常希望"}
                        ]}
                        """);

        assertThat(extractor.extractImplicit(userMessage, "收到", "model-1"))
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.semanticKey()).isEqualTo("preference.answer_order");
                    assertThat(candidate.content()).isEqualTo("结论优先");
                    assertThat(candidate.explicit()).isFalse();
                });
    }
}
