package com.ai.controller;

import com.ai.agent.dto.AgentProfileListResponse;
import com.ai.agent.dto.AgentProfileMutationResponse;
import com.ai.agent.dto.AgentProfileRequest;
import com.ai.agent.dto.AgentProfileResponse;
import com.ai.agent.dto.AgentRegistryResponse;
import com.ai.agent.dto.AgentTestRequest;
import com.ai.model.AnalysisResponse;
import com.ai.service.AgentProfileService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
     * 查询当前租户 Agent 注册中心快照。
     *
     * @return Agent 注册中心快照
     */
    @GetMapping("/registry")
    public AgentRegistryResponse registry() {
        return agentProfileService.getRegistry();
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
     * 创建 Agent 配置。
     *
     * @param request 创建请求
     * @return 创建结果
     */
    @PostMapping
    public AgentProfileMutationResponse create(@RequestBody AgentProfileRequest request) {
        return agentProfileService.createAgent(request);
    }

    /**
     * 更新 Agent 配置。
     *
     * @param agentId Agent 编号
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping("/{agentId}")
    public AgentProfileMutationResponse update(@PathVariable String agentId,
            @RequestBody AgentProfileRequest request) {
        return agentProfileService.updateAgent(agentId, request);
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

    /**
     * 试运行指定 Agent 配置。
     *
     * @param agentId Agent 编号
     * @param request 试运行请求
     * @return Agent 执行结果
     */
    @PostMapping("/{agentId}/test")
    public AnalysisResponse test(@PathVariable String agentId, @RequestBody AgentTestRequest request) {
        return agentProfileService.testAgent(agentId, request);
    }

    /**
     * 删除 Agent 配置。
     *
     * @param agentId Agent 编号
     * @return 删除结果
     */
    @DeleteMapping("/{agentId}")
    public AgentProfileMutationResponse delete(@PathVariable String agentId) {
        return agentProfileService.delete(agentId);
    }
}
