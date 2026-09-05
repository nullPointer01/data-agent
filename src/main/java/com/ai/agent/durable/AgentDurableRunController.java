package com.ai.agent.durable;

import com.ai.agent.durable.dto.AgentDurableRunResponse;
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

    public AgentDurableRunController(AgentDurableRunService runService) {
        this.runService = runService;
    }

    @GetMapping("/{runId}")
    public AgentDurableRunResponse detail(@PathVariable String runId) {
        return runService.getOwned(runId);
    }

    @PostMapping("/{runId}/cancel")
    public AgentDurableRunResponse cancel(@PathVariable String runId) {
        return runService.cancelWaitingOwned(runId);
    }
}
