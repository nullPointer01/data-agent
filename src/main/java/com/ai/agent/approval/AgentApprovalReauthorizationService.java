package com.ai.agent.approval;

import com.ai.agent.durable.AgentRunCheckpoint;
import com.ai.agent.durable.AgentRunStateEntity;
import com.ai.agent.tool.governance.AgentToolAuthorizationService;
import com.ai.agent.tool.governance.AgentToolAuthorizationSnapshot;
import com.ai.agent.tool.governance.AgentToolDescriptor;
import com.ai.agent.tool.governance.AgentToolRegistry;
import com.ai.security.SecurityConstants;
import com.ai.security.SysUser;
import com.ai.security.SysUserRepository;
import com.ai.security.rbac.RolePermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * 恢复前重读 owner、reviewer、Tool Registry、allowlist 和风险策略。
 *
 * @author data-agent
 */
@Service
public class AgentApprovalReauthorizationService {

    private final AgentToolAuthorizationService authorizationService;
    private final AgentToolRegistry toolRegistry;
    private final SysUserRepository userRepository;
    private final RolePermissionService rolePermissionService;

    public AgentApprovalReauthorizationService(AgentToolAuthorizationService authorizationService,
            AgentToolRegistry toolRegistry,
            SysUserRepository userRepository,
            RolePermissionService rolePermissionService) {
        this.authorizationService = authorizationService;
        this.toolRegistry = toolRegistry;
        this.userRepository = userRepository;
        this.rolePermissionService = rolePermissionService;
    }

    @Transactional(readOnly = true)
    public AgentApprovalReauthorization reauthorize(AgentRunStateEntity run,
            AgentToolApprovalEntity approval,
            AgentRunCheckpoint checkpoint) {
        requireMatchingState(run, approval, checkpoint);
        AgentToolDescriptor descriptor = toolRegistry.find(approval.getToolName())
                .filter(entry -> toolRegistry.isEnabled(entry.descriptor().name()))
                .map(AgentToolRegistry.RegisteredTool::descriptor)
                .filter(AgentToolDescriptor::approvalRequired)
                .orElseThrow(() -> denied("工具已停用或不再需要审批"));
        if (!checkpoint.allowedToolsAtRequest().contains(descriptor.name())) {
            throw denied("原执行者白名单不允许该工具");
        }
        AgentToolAuthorizationSnapshot ownerAuthorization = authorizationService.resolve(
                run.getUserId(), run.getTenantId());
        if (!ownerAuthorization.permits(descriptor.name(), descriptor.requiredPermission())) {
            throw denied("发起人当前已无工具执行权限");
        }
        SysUser reviewer = userRepository.findById(approval.getReviewerUserId())
                .filter(SysUser::isEnabled)
                .filter(user -> Objects.equals(run.getTenantId(), user.getTenantId()))
                .orElseThrow(() -> denied("审批人已停用或租户不匹配"));
        if (Objects.equals(run.getUserId(), reviewer.getId())
                || !rolePermissionService.hasPermission(
                        reviewer.getRoles(), SecurityConstants.PERMISSION_AGENT_APPROVAL_REVIEW)
                || !rolePermissionService.hasPermission(reviewer.getRoles(), descriptor.requiredPermission())
                || !rolePermissionService.hasPermission(reviewer.getRoles(), descriptor.approvalPermission())
                || !Objects.equals(descriptor.approvalPermission(), approval.getApprovalPermission())) {
            throw denied("审批人当前权限不满足执行条件");
        }
        AgentApprovalGrant grant = new AgentApprovalGrant(
                approval.getApprovalId(),
                run.getRunId(),
                run.getTenantId(),
                run.getUserId(),
                reviewer.getId(),
                approval.getToolCallId(),
                approval.getToolName(),
                approval.getExpiresAt());
        return new AgentApprovalReauthorization(grant, ownerAuthorization, descriptor);
    }

    private void requireMatchingState(AgentRunStateEntity run,
            AgentToolApprovalEntity approval,
            AgentRunCheckpoint checkpoint) {
        if (run == null || approval == null || checkpoint == null
                || approval.getDecisionStatus() != AgentApprovalDecisionStatus.APPROVED
                || (approval.getExecutionStatus() != AgentApprovalExecutionStatus.READY
                    && approval.getExecutionStatus() != AgentApprovalExecutionStatus.RUNNING)
                || approval.getExpiresAt() == null
                || !approval.getExpiresAt().isAfter(Instant.now())
                || !Objects.equals(run.getTenantId(), approval.getTenantId())
                || !Objects.equals(run.getRunId(), approval.getRunId())
                || !Objects.equals(run.getUserId(), approval.getRequesterUserId())
                || !Objects.equals(checkpoint.pendingTool().toolCallId(), approval.getToolCallId())
                || !Objects.equals(checkpoint.pendingTool().toolName(), approval.getToolName())) {
            throw denied("审批、Run 与 Checkpoint 状态不一致");
        }
    }

    private SecurityException denied(String detail) {
        return new SecurityException("恢复授权失败: " + detail);
    }
}
