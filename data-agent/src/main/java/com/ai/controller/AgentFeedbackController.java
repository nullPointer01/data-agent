package com.ai.controller;

import com.ai.agent.dto.AgentFeedbackDashboardResponse;
import com.ai.agent.dto.AgentFeedbackInsightResponse;
import com.ai.agent.dto.AgentFeedbackListResponse;
import com.ai.agent.dto.AgentFeedbackMutationResponse;
import com.ai.agent.dto.AgentFeedbackRequest;
import com.ai.agent.dto.AgentFeedbackSummaryResponse;
import com.ai.service.AgentFeedbackService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 回答质量反馈接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/agent-feedbacks")
public class AgentFeedbackController {

    private static final int DEFAULT_LIMIT = 100;

    private final AgentFeedbackService feedbackService;

    public AgentFeedbackController(AgentFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * 提交当前用户对回答的反馈。
     *
     * @param request 反馈请求
     * @return 保存结果
     */
    @PostMapping
    public AgentFeedbackMutationResponse saveFeedback(@RequestBody AgentFeedbackRequest request) {
        return feedbackService.saveCurrentUserFeedback(request);
    }

    /**
     * 查询反馈管理台聚合数据。
     *
     * @param limit 反馈列表返回条数
     * @param traceId 可选执行轨迹编号
     * @param rating 可选评分
     * @return 反馈管理台数据
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public AgentFeedbackDashboardResponse dashboard(
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "rating", required = false) String rating) {
        return feedbackService.getCurrentTenantDashboard(limit, traceId, rating);
    }

    /**
     * 查询当前租户下的反馈质量摘要。
     *
     * @return 反馈质量摘要
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public AgentFeedbackSummaryResponse summary() {
        return feedbackService.summarizeCurrentTenantFeedbacks();
    }

    /**
     * 查询当前租户下的反馈质量洞察。
     *
     * @return 反馈质量洞察
     */
    @GetMapping("/insights")
    @PreAuthorize("hasRole('ADMIN')")
    public AgentFeedbackInsightResponse insights() {
        return feedbackService.analyzeCurrentTenantFeedbacks();
    }

    /**
     * 查询当前租户下的反馈列表。
     *
     * @param limit 返回条数
     * @param traceId 可选执行轨迹编号
     * @param rating 可选评分
     * @return 反馈列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AgentFeedbackListResponse listFeedbacks(
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "rating", required = false) String rating) {
        return feedbackService.listCurrentTenantFeedbacks(limit, traceId, rating);
    }
}
