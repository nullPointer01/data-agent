package com.ai.agent.approval.dto;

import java.util.List;

/**
 * 有界审批列表。
 *
 * @param items 审批项
 * @param count 当前返回数量
 * @author data-agent
 */
public record AgentApprovalListResponse(List<AgentApprovalResponse> items, int count) {

    public AgentApprovalListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
