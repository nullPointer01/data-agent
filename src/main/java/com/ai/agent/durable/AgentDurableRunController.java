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

    @GetMapping("/{runId}")
    public AgentRunEvidenceResponse detail(@PathVariable String runId) {
        return evidenceService.getOwned(runId);
    }

    @PostMapping("/{runId}/cancel")
    public AgentDurableRunResponse cancel(@PathVariable String runId) {
        return runService.cancelWaitingOwned(runId);
    }
}
