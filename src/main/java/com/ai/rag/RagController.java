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

    /**
     * 查询当前 RAG 检索链路的生效配置。
     *
     * @return 检索开关、候选数量、阈值和后端类型
     */
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

    /**
     * 检查 RAG 所需的向量、全文检索和重排依赖状态。
     *
     * @return RAG 健康检查结果
     */
    @GetMapping("/health")
    public RagHealthResponse getHealth() {
        return ragHealthService.getHealth();
    }

    /**
     * 使用当前租户知识执行一次真实 RAG 检索。
     *
     * @param request 查询文本
     * @return 检索上下文、引用和执行轨迹
     */
    @PostMapping("/retrieve")
    public RagContextResponse retrieve(@RequestBody RagProbeRequest request) {
        return ragRetrievalService.retrieve(request == null ? "" : request.query());
    }

    /**
     * 使用确定性规则评估一条 RAG 检索结果的质量。
     *
     * @param request 可选的评估输入和期望证据
     * @return RAG 质量指标和验收建议
     */
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
