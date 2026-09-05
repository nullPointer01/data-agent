package com.ai.rag.eval;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 黄金集批量评测管理接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/rag/benchmark")
public class RagBenchmarkController {

    private final RagBenchmarkService benchmarkService;

    public RagBenchmarkController(RagBenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    /**
     * 使用当前租户知识索引运行配置好的固定黄金集。
     *
     * @return 分阶段评测报告
     */
    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public RagBenchmarkReport run() {
        return benchmarkService.runCurrentTenant();
    }
}
