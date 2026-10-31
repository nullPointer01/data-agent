package com.ai.agent.capability;

import com.ai.api.CursorPageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户的只读 Agent 能力目录。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/my/capabilities")
public class AgentCapabilityController {

    private final AgentCapabilityService capabilityService;

    public AgentCapabilityController(AgentCapabilityService capabilityService) {
        this.capabilityService = capabilityService;
    }

    /**
     * 返回经租户、所有权和 RBAC 过滤的能力描述符。
     *
     * @param agentId 正在编辑的 Agent 编号，可为空
     * @return 当前用户能力目录
     */
    @GetMapping
    public CursorPageResponse<AgentCapabilityDescriptor> list(
            @RequestParam(required = false) String agentId, @RequestParam AgentCapabilityType type,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String after,
            @RequestParam(required = false) String query) {
        return capabilityService.currentUserDirectory(agentId, type, size, after, query);
    }
}
