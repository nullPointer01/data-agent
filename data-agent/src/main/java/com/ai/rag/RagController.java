package com.ai.rag;

import com.ai.rag.dto.RagContextResponse;
import com.ai.rag.dto.RagHealthResponse;
import com.ai.rag.dto.RagQualityEvaluationRequest;
import com.ai.rag.dto.RagQualityEvaluationResponse;
import com.ai.rag.dto.RagSettingsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 管理接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private final RagProperties ragProperties;

    private final RagRetrievalService ragRetrievalService;

    private final RagHealthService ragHealthService;

    private final RagQualityEvaluationService ragQualityEvaluationService;

    public RagController(RagProperties ragProperties, RagRetrievalService ragRetrievalService,
            RagHealthService ragHealthService, RagQualityEvaluationService ragQualityEvaluationService) {
        this.ragProperties = ragProperties;
        this.ragRetrievalService = ragRetrievalService;
        this.ragHealthService = ragHealthService;
        this.ragQualityEvaluationService = ragQualityEvaluationService;
    }

    @GetMapping("/settings")
    public RagSettingsResponse getSettings() {
        RagSettingsResponse response = new RagSettingsResponse();
        response.setEnabled(ragProperties.isEnabled());
        response.setTopK(ragProperties.getTopK());
        response.setCandidateTopK(ragProperties.getCandidateTopK());
        response.setMinScore(ragProperties.getMinScore());
        response.setMaxContextChars(ragProperties.getMaxContextChars());
        response.setFullTextProvider(ragProperties.getFullTextProvider());
        response.setVectorProvider("milvus");
        return response;
    }

    @GetMapping("/health")
    public RagHealthResponse getHealth() {
        return ragHealthService.getHealth();
    }

    @PostMapping("/retrieve")
    public RagContextResponse retrieve(@RequestBody RagProbeRequest request) {
        return ragRetrievalService.retrieve(request == null ? "" : request.query());
    }

    @PostMapping("/evaluate")
    public RagQualityEvaluationResponse evaluate(@RequestBody(required = false) RagQualityEvaluationRequest request) {
        return ragQualityEvaluationService.evaluate(request);
    }

    /**
     * RAG 检索探测请求。
     *
     * @param query 查询内容
     */
    public record RagProbeRequest(String query) {
    }
}
