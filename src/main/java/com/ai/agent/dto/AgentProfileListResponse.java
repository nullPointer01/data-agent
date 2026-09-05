package com.ai.agent.dto;

import java.util.List;

/**
 * Agent 配置列表响应。
 *
 * @author data-agent
 */
public record AgentProfileListResponse(boolean success, List<AgentProfileResponse> agents) {
}
