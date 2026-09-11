package com.ai.controller;

import com.ai.agent.dto.AgentProfileListResponse;
import com.ai.agent.dto.AgentProfileMutationResponse;
import com.ai.agent.dto.AgentProfileRequest;
import com.ai.agent.dto.AgentProfileResponse;
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
 * 普通用户自定义 Agent 管理接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/my/agents")
public class CustomAgentController {

    private final AgentProfileService agentProfileService;

    public CustomAgentController(AgentProfileService agentProfileService) {
        this.agentProfileService = agentProfileService;
    }

    /**
     * 查询当前用户自定义 Agent 列表。
     *
     * @param enabledOnly 是否只返回启用配置
     * @return Agent 配置列表
     */
    @GetMapping
    public AgentProfileListResponse list(@RequestParam(defaultValue = "false") boolean enabledOnly) {
        return agentProfileService.listMyAgents(enabledOnly);
    }

    /**
     * 获取当前用户的默认个人 Agent，首次访问时自动创建。
     */
    @GetMapping("/default")
    public AgentProfileResponse defaultAgent() {
        return agentProfileService.getOrCreateMyDefaultAgent();
    }

    /**
     * 查询当前用户自定义 Agent 详情。
     *
     * @param agentId Agent 编号
     * @return Agent 配置详情
     */
    @GetMapping("/{agentId}")
    public AgentProfileResponse detail(@PathVariable String agentId) {
        return agentProfileService.getMyAgent(agentId);
    }

    /**
     * 创建当前用户自定义 Agent。
     *
     * @param request 创建请求
     * @return 创建结果
     */
    @PostMapping
    public AgentProfileMutationResponse create(@RequestBody AgentProfileRequest request) {
        return agentProfileService.createMyAgent(request);
    }

    /**
     * 更新当前用户自定义 Agent。
     *
     * @param agentId Agent 编号
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping("/{agentId}")
    public AgentProfileMutationResponse update(@PathVariable String agentId,
            @RequestBody AgentProfileRequest request) {
        return agentProfileService.updateMyAgent(agentId, request);
    }

    /**
     * 将指定配置设为当前用户的默认 Agent。
     */
    @PutMapping("/{agentId}/default")
    public AgentProfileResponse setDefault(@PathVariable String agentId) {
        return agentProfileService.setMyDefaultAgent(agentId);
    }

    /**
     * 启用或停用当前用户自定义 Agent。
     *
     * @param agentId Agent 编号
     * @return 状态变更结果
     */
    @PutMapping("/{agentId}/enabled")
    public AgentProfileMutationResponse toggle(@PathVariable String agentId) {
        return agentProfileService.toggleMyAgent(agentId);
    }

    /**
     * 删除当前用户自定义 Agent。
     *
     * @param agentId Agent 编号
     * @return 删除结果
     */
    @DeleteMapping("/{agentId}")
    public AgentProfileMutationResponse delete(@PathVariable String agentId) {
        return agentProfileService.deleteMyAgent(agentId);
    }
}
