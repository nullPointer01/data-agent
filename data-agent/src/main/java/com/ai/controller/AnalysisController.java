package com.ai.controller;

import com.ai.agent.DataAnalysisAgent;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.service.AnalysisStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 分析与对话会话接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisController.class);

    private final DataAnalysisAgent agent;
    private final AnalysisStreamService analysisStreamService;

    public AnalysisController(DataAnalysisAgent agent, AnalysisStreamService analysisStreamService) {
        this.agent = agent;
        this.analysisStreamService = analysisStreamService;
    }

    /**
     * 同步执行一次分析请求。
     *
     * @param request 分析请求
     * @return 分析结果
     */
    @PostMapping("/analyze")
    public AnalysisResponse analyze(@RequestBody AnalysisRequest request) {
        LOGGER.info("收到同步分析请求，question={}", request.getQuestion());
        return agent.analyze(request);
    }

    /**
     * 通过 SSE 返回分析结果。
     *
     * @param request 分析请求
     * @return SSE 发射器
     */
    @PostMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeStream(@RequestBody AnalysisRequest request) {
        return analysisStreamService.stream(request);
    }
}
