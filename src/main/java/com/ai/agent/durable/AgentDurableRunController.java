package com.ai.agent.durable;

import com.ai.agent.durable.dto.AgentDurableRunResponse;
import com.ai.agent.runtime.AgentRunEvidenceService;
import com.ai.agent.runtime.dto.AgentRunEvidenceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 持久化 Agent Run 所有者接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/agent-runs")
public class AgentDurableRunController {

    private final AgentDurableRunService runService;
    private final AgentRunEvidenceService evidenceService;

    public AgentDurableRunController(AgentDurableRunService runService,
            AgentRunEvidenceService evidenceService) {
        this.runService = runService;
        this.evidenceService = evidenceService;
    }

    /**
     * 查询当前用户拥有的持久化 Run 及其执行证据。
     *
     * @param runId Run 编号
     * @return Run 状态和执行证据
     */
    @GetMapping("/{runId}")
    public AgentRunEvidenceResponse detail(@PathVariable String runId) {
        return evidenceService.getOwned(runId);
    }

    /**
     * 取消当前用户拥有且仍在等待审批的 Run。
     *
     * @param runId Run 编号
     * @return 取消后的 Run 状态
     */
    @PostMapping("/{runId}/cancel")
    public AgentDurableRunResponse cancel(@PathVariable String runId) {
        return runService.cancelWaitingOwned(runId);
    }
}
