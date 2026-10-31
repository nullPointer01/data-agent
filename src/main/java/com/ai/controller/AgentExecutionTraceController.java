package com.ai.controller;

import com.ai.api.CursorPageResponse;
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


    private final AgentExecutionTraceService traceService;

    public AgentExecutionTraceController(AgentExecutionTraceService traceService) {
        this.traceService = traceService;
    }

    /**
     * 查询当前租户下的执行轨迹。
     *
     * @param size 每页数量
     * @param userId 可选用户编号
     * @param query 可选搜索内容
     * @param after 上一页游标
     * @return 执行轨迹列表
     */
    @GetMapping
    public CursorPageResponse<AgentExecutionTraceResponse> listTraces(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String after) {
        return traceService.listCurrentTenant(size, userId, query, after);
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
