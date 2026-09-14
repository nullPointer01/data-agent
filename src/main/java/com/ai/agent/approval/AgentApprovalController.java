package com.ai.agent.approval;

import com.ai.agent.approval.dto.AgentApprovalDecisionRequest;
import com.ai.agent.approval.dto.AgentApprovalListResponse;
import com.ai.agent.approval.dto.AgentApprovalResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 工具动作审批接口。所有权限和租户校验由 AgentApprovalService 重读数据库执行。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/agent-approvals")
public class AgentApprovalController {

    private final AgentApprovalService approvalService;

    public AgentApprovalController(AgentApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * 查询当前租户内的动作审批，可按审批状态过滤并限制返回数量。
     *
     * @param status 可选的审批状态
     * @param limit 最大返回数量
     * @return 审批列表
     */
    @GetMapping
    public AgentApprovalListResponse list(
            @RequestParam(required = false) AgentApprovalDecisionStatus status,
            @RequestParam(defaultValue = "50") int limit) {
        return approvalService.list(status, limit);
    }

    /**
     * 查询当前租户内一条审批的完整信息。
     *
     * @param approvalId 审批编号
     * @return 审批详情
     */
    @GetMapping("/{approvalId}")
    public AgentApprovalResponse detail(@PathVariable String approvalId) {
        return approvalService.detail(approvalId);
    }

    /**
     * 批准一条待处理的 Agent 工具动作。
     *
     * @param approvalId 审批编号
     * @param request 审批意见
     * @return 更新后的审批信息
     */
    @PostMapping("/{approvalId}/approve")
    public AgentApprovalResponse approve(@PathVariable String approvalId,
            @Valid @RequestBody AgentApprovalDecisionRequest request) {
        return approvalService.decide(approvalId, AgentApprovalDecision.APPROVE, request.comment());
    }

    /**
     * 拒绝一条待处理的 Agent 工具动作。
     *
     * @param approvalId 审批编号
     * @param request 审批意见
     * @return 更新后的审批信息
     */
    @PostMapping("/{approvalId}/reject")
    public AgentApprovalResponse reject(@PathVariable String approvalId,
            @Valid @RequestBody AgentApprovalDecisionRequest request) {
        return approvalService.decide(approvalId, AgentApprovalDecision.REJECT, request.comment());
    }
}
