package com.ai.agent.approval;

import com.ai.agent.approval.dto.AgentApprovalListResponse;
import com.ai.agent.approval.dto.AgentApprovalResponse;
import com.ai.agent.durable.AgentDurableRunStore;
import com.ai.agent.durable.AgentDurableRuntimeProperties;
import com.ai.agent.durable.AgentRunTransition;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.runtime.AgentRunTerminationReason;
import com.ai.agent.tool.governance.AgentToolDescriptor;
import com.ai.agent.tool.governance.AgentToolRegistry;
import com.ai.security.SecurityConstants;
import com.ai.security.SecurityContextHelper;
import com.ai.security.SysUser;
import com.ai.security.SysUserRepository;
import com.ai.security.rbac.RolePermissionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 审批查询、不可变决定和过期联动的安全边界。
 *
 * @author data-agent
 */
@Service
public class AgentApprovalService {

    private static final String NOT_FOUND_OR_DENIED = "审批不存在或无权限";
    private static final int MAX_LIST_LIMIT = 100;

    private final AgentToolApprovalRepository approvalRepository;
    private final AgentDurableRunStore runStore;
    private final AgentDurableRuntimeProperties properties;
    private final AgentToolRegistry toolRegistry;
    private final SysUserRepository userRepository;
    private final RolePermissionService rolePermissionService;
    private final SecurityContextHelper securityContextHelper;
    private final AgentApprovalTelemetry telemetry;

    public AgentApprovalService(AgentToolApprovalRepository approvalRepository,
            AgentDurableRunStore runStore,
            AgentDurableRuntimeProperties properties,
            AgentToolRegistry toolRegistry,
            SysUserRepository userRepository,
            RolePermissionService rolePermissionService,
            SecurityContextHelper securityContextHelper,
            AgentApprovalTelemetry telemetry) {
        this.approvalRepository = approvalRepository;
        this.runStore = runStore;
        this.properties = properties;
        this.toolRegistry = toolRegistry;
        this.userRepository = userRepository;
        this.rolePermissionService = rolePermissionService;
        this.securityContextHelper = securityContextHelper;
        this.telemetry = telemetry;
    }

    @Transactional(readOnly = true)
    public AgentApprovalListResponse list(AgentApprovalDecisionStatus status, int limit) {
        SysUser reviewer = requireReviewer();
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, MAX_LIST_LIMIT)));
        List<AgentToolApprovalEntity> approvals = status == null
                ? approvalRepository.findByTenantIdOrderByRequestedAtDesc(reviewer.getTenantId(), page)
                : approvalRepository.findByTenantIdAndDecisionStatusOrderByRequestedAtDesc(
                        reviewer.getTenantId(), status, page);
        List<AgentApprovalResponse> items = approvals.stream().map(this::toResponse).toList();
        return new AgentApprovalListResponse(items, items.size());
    }

    @Transactional(readOnly = true)
    public AgentApprovalResponse detail(String approvalId) {
        SysUser reviewer = requireReviewer();
        return toResponse(findTenantApproval(approvalId, reviewer.getTenantId()));
    }

    @Transactional
    public AgentApprovalResponse decide(String approvalId, AgentApprovalDecision decision, String comment) {
        if (decision == null) {
            throw new IllegalArgumentException("审批决定不能为空");
        }
        SysUser reviewer = requireReviewer();
        AgentToolApprovalEntity approval = findTenantApproval(approvalId, reviewer.getTenantId());
        validateDecision(reviewer, approval);
        if (approval.getDecisionStatus() != AgentApprovalDecisionStatus.PENDING) {
            return toResponse(approval);
        }
        Instant now = Instant.now();
        if (!now.isBefore(approval.getExpiresAt())) {
            expireOne(approval, now);
            return toResponse(findTenantApproval(approvalId, reviewer.getTenantId()));
        }
        AgentApprovalDecisionStatus targetDecision = decision == AgentApprovalDecision.APPROVE
                ? AgentApprovalDecisionStatus.APPROVED
                : AgentApprovalDecisionStatus.REJECTED;
        AgentApprovalExecutionStatus targetExecution = decision == AgentApprovalDecision.APPROVE
                ? AgentApprovalExecutionStatus.READY
                : AgentApprovalExecutionStatus.CANCELLED;
        int changed = approvalRepository.decide(
                approvalId,
                reviewer.getTenantId(),
                targetDecision,
                targetExecution,
                reviewer.getId(),
                normalizeComment(comment),
                now,
                approval.getVersion());
        if (changed != 1) {
            return toResponse(findTenantApproval(approvalId, reviewer.getTenantId()));
        }
        if (decision == AgentApprovalDecision.REJECT) {
            boolean rejected = runStore.transition(
                    approval.getRunId(),
                    new AgentRunTransition(
                            AgentRunStatus.WAITING_APPROVAL,
                            AgentRunStatus.REJECTED,
                            AgentRunTerminationReason.APPROVAL_REJECTED,
                            "工具动作已被拒绝"));
            if (!rejected) {
                throw new IllegalStateException("审批拒绝与 Run 状态发生冲突");
            }
        }
        telemetry.record(
                decision == AgentApprovalDecision.APPROVE ? "APPROVE" : "REJECT",
                targetDecision.name(),
                approval.getTenantId(),
                reviewer.getId(),
                reviewer.getUsername(),
                approvalId,
                approval.getRunId(),
                approval.getToolCallId(),
                approval.getToolName());
        return toResponse(findTenantApproval(approvalId, reviewer.getTenantId()));
    }

    @Transactional
    public int expireDue() {
        if (!properties.isEnabled()) {
            return 0;
        }
        Instant now = Instant.now();
        List<AgentToolApprovalEntity> candidates = approvalRepository
                .findByDecisionStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        AgentApprovalDecisionStatus.PENDING,
                        now,
                        PageRequest.of(0, properties.getScanBatchSize()));
        int expired = 0;
        for (AgentToolApprovalEntity approval : candidates) {
            if (expireOne(approval, now)) {
                expired++;
            }
        }
        return expired;
    }

    private boolean expireOne(AgentToolApprovalEntity approval, Instant now) {
        int changed = approvalRepository.expire(approval.getApprovalId(), now, approval.getVersion());
        if (changed != 1) {
            return false;
        }
        runStore.transition(
                approval.getRunId(),
                new AgentRunTransition(
                        AgentRunStatus.WAITING_APPROVAL,
                        AgentRunStatus.EXPIRED,
                        AgentRunTerminationReason.APPROVAL_EXPIRED,
                        "工具动作审批已过期"));
        telemetry.record(
                "EXPIRE",
                AgentApprovalDecisionStatus.EXPIRED.name(),
                approval.getTenantId(),
                approval.getRequesterUserId(),
                "system",
                approval.getApprovalId(),
                approval.getRunId(),
                approval.getToolCallId(),
                approval.getToolName());
        return true;
    }

    private SysUser requireReviewer() {
        String userId = securityContextHelper.getCurrentUserId();
        String tenantId = securityContextHelper.getCurrentTenantId();
        SysUser reviewer = userRepository.findById(userId == null ? "" : userId)
                .filter(SysUser::isEnabled)
                .filter(user -> Objects.equals(tenantId, user.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException(NOT_FOUND_OR_DENIED));
        if (!rolePermissionService.hasPermission(
                reviewer.getRoles(), SecurityConstants.PERMISSION_AGENT_APPROVAL_REVIEW)) {
            throw new IllegalArgumentException(NOT_FOUND_OR_DENIED);
        }
        return reviewer;
    }

    private AgentToolApprovalEntity findTenantApproval(String approvalId, String tenantId) {
        return approvalRepository.findByApprovalIdAndTenantId(approvalId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException(NOT_FOUND_OR_DENIED));
    }

    private void validateDecision(SysUser reviewer, AgentToolApprovalEntity approval) {
        if (Objects.equals(reviewer.getId(), approval.getRequesterUserId())) {
            throw new IllegalArgumentException("发起人不能审批自己的工具动作");
        }
        AgentToolDescriptor descriptor = toolRegistry.find(approval.getToolName())
                .map(AgentToolRegistry.RegisteredTool::descriptor)
                .filter(AgentToolDescriptor::approvalRequired)
                .orElseThrow(() -> new IllegalArgumentException(NOT_FOUND_OR_DENIED));
        boolean hasApprovalPermission = rolePermissionService.hasPermission(
                reviewer.getRoles(), approval.getApprovalPermission());
        boolean currentPolicyMatches = Objects.equals(
                descriptor.approvalPermission(), approval.getApprovalPermission());
        boolean canExecuteTool = rolePermissionService.hasPermission(
                reviewer.getRoles(), descriptor.requiredPermission());
        if (!hasApprovalPermission || !currentPolicyMatches || !canExecuteTool) {
            throw new IllegalArgumentException(NOT_FOUND_OR_DENIED);
        }
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String normalized = comment.strip();
        if (normalized.length() > 512) {
            throw new IllegalArgumentException("审批备注不能超过512个字符");
        }
        return normalized;
    }

    private AgentApprovalResponse toResponse(AgentToolApprovalEntity approval) {
        return new AgentApprovalResponse(
                approval.getApprovalId(),
                approval.getRunId(),
                approval.getToolCallId(),
                approval.getToolName(),
                approval.getRiskLevel().name(),
                approval.getRequesterUserId(),
                approval.getSafeArgumentSummary(),
                approval.getDecisionStatus().name(),
                approval.getExecutionStatus().name(),
                approval.getReviewerUserId(),
                approval.getDecisionComment(),
                approval.getRequestedAt(),
                approval.getExpiresAt(),
                approval.getDecidedAt(),
                approval.getExecutionStartedAt(),
                approval.getExecutionCompletedAt());
    }
}
