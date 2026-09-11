package com.ai.controller;

import com.ai.agent.dto.AgentProfileListResponse;
import com.ai.agent.dto.AgentProfileMutationResponse;
import com.ai.agent.dto.AgentProfileResponse;
import com.ai.service.AgentProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前租户下 Agent 配置管理接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentProfileController {

    private final AgentProfileService agentProfileService;

    public AgentProfileController(AgentProfileService agentProfileService) {
        this.agentProfileService = agentProfileService;
    }

    /**
     * 查询 Agent 配置列表。
     *
     * @param enabledOnly 是否只返回启用配置
     * @return Agent 配置列表
     */
    @GetMapping
    public AgentProfileListResponse list(@RequestParam(defaultValue = "false") boolean enabledOnly) {
        return agentProfileService.listAgents(enabledOnly);
    }

    /**
     * 查询单个 Agent 配置详情。
     *
     * @param agentId Agent 编号
     * @return Agent 配置详情
     */
    @GetMapping("/{agentId}")
    public AgentProfileResponse detail(@PathVariable String agentId) {
        return agentProfileService.getAgent(agentId);
    }

    /**
     * 启用或停用 Agent 配置。
     *
     * @param agentId Agent 编号
     * @return 状态变更结果
     */
    @PutMapping("/{agentId}/enabled")
    public AgentProfileMutationResponse toggle(@PathVariable String agentId) {
        return agentProfileService.toggle(agentId);
    }

}
