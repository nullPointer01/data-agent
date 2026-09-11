package com.ai.agent.eval;

import com.ai.agent.eval.dto.AgentEvalReportResponse;
import com.ai.agent.eval.dto.AgentEvalReportResponse.DatasetSummary;
import com.ai.agent.eval.dto.AgentEvalReportResponse.RunSummary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 管理员使用的版本化 Agent Eval 执行与报告接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/agent-evals")
@PreAuthorize("hasRole('ADMIN')")
public class AgentEvalController {

    private static final int DEFAULT_LIMIT = 30;

    private final AgentEvalService evalService;
    private final Executor agentEvalExecutor;

    public AgentEvalController(AgentEvalService evalService,
            @Qualifier("agentEvalExecutor") Executor agentEvalExecutor) {
        this.evalService = evalService;
        this.agentEvalExecutor = agentEvalExecutor;
    }

    /** 查询当前租户可运行的评测数据集版本。 */
    @GetMapping("/datasets")
    public List<DatasetSummary> datasets() {
        return evalService.listDatasets();
    }

    /** 创建评测运行，并提交到传播认证上下文的有界 Agent 线程池。 */
    @PostMapping("/runs")
    public AgentEvalReportResponse start(@RequestBody StartEvalRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("评测请求不能为空");
        }
        AgentEvalReportResponse started = evalService.startEvaluation(request.datasetId(), request.agentId());
        try {
            agentEvalExecutor.execute(() -> evalService.executeEvaluation(started.evalRunId()));
        } catch (RejectedExecutionException exception) {
            evalService.markFailed(started.evalRunId(), "评测任务队列已满");
            throw new IllegalArgumentException("评测任务队列已满，请稍后重试");
        }
        return started;
    }

    /** 查询最近的评测运行摘要。 */
    @GetMapping("/runs")
    public List<RunSummary> runs(
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return evalService.listRuns(limit);
    }

    /** 查询单次评测报告及样本级 Run 下钻引用。 */
    @GetMapping("/runs/{evalRunId}")
    public AgentEvalReportResponse report(@PathVariable String evalRunId) {
        return evalService.getReport(evalRunId);
    }

    /** 启动评测所需的固定数据集与 Agent 标识。 */
    public record StartEvalRequest(String datasetId, String agentId) {
    }
}
