package com.ai.controller;

import com.ai.agent.dto.AgentQualityDashboardResponse;
import com.ai.agent.dto.AgentReasoningHealthResponse;
import com.ai.service.AgentQualityService;
import com.ai.service.AgentReasoningHealthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 质量评估接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/agent-quality")
public class AgentQualityController {

    private static final int DEFAULT_LIMIT = 200;

    private final AgentQualityService agentQualityService;
    private final AgentReasoningHealthService reasoningHealthService;

    public AgentQualityController(AgentQualityService agentQualityService,
            AgentReasoningHealthService reasoningHealthService) {
        this.agentQualityService = agentQualityService;
        this.reasoningHealthService = reasoningHealthService;
    }

    /**
     * 查询当前租户 Agent 质量看板。
     *
     * @param limit 最近执行轨迹样本数量
     * @return Agent 质量看板
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public AgentQualityDashboardResponse dashboard(
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return agentQualityService.getCurrentTenantDashboard(limit);
    }

    /**
     * 查询当前租户 Agent 增强推理验收状态。
     *
     * @param limit 最近执行轨迹样本数量
     * @return Agent 增强推理健康状态
     */
    @GetMapping("/reasoning-health")
    @PreAuthorize("hasRole('ADMIN')")
    public AgentReasoningHealthResponse reasoningHealth(
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return reasoningHealthService.getCurrentTenantHealth(limit);
    }
}
