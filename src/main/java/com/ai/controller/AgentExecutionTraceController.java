package com.ai.controller;

import com.ai.agent.dto.AgentExecutionTraceListResponse;
import com.ai.agent.dto.AgentExecutionTraceResponse;
import com.ai.service.AgentExecutionTraceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 执行轨迹查询接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/agent-traces")
public class AgentExecutionTraceController {

    private static final int DEFAULT_LIMIT = 50;

    private final AgentExecutionTraceService traceService;

    public AgentExecutionTraceController(AgentExecutionTraceService traceService) {
        this.traceService = traceService;
    }

    /**
     * 查询当前租户下的执行轨迹。
     *
     * @param limit 返回条数
     * @param userId 可选用户编号
     * @return 执行轨迹列表
     */
    @GetMapping
    public AgentExecutionTraceListResponse listTraces(
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(value = "userId", required = false) String userId) {
        return traceService.listCurrentTenant(limit, userId);
    }

    /**
     * 查询单条执行轨迹详情。
     *
     * @param traceId 轨迹编号
     * @return 执行轨迹详情
     */
    @GetMapping("/{traceId}")
    public AgentExecutionTraceResponse detail(@PathVariable String traceId) {
        return traceService.getCurrentTenantTrace(traceId);
    }
}
