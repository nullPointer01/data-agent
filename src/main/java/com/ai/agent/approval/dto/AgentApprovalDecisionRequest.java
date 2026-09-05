package com.ai.agent.approval.dto;

import jakarta.validation.constraints.Size;

/**
 * 审批决定请求。客户端不能修改工具、参数或恢复状态。
 *
 * @param comment 可选审批备注
 * @author data-agent
 */
public record AgentApprovalDecisionRequest(
        @Size(max = 512, message = "审批备注不能超过512个字符") String comment) {
}
