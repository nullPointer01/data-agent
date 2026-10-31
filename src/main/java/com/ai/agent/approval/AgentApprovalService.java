package com.ai.agent.approval;

import com.ai.api.ListPageResponse;
import com.ai.api.ListPaging;
import com.ai.agent.approval.dto.AgentApprovalResponse;
import com.ai.agent.durable.AgentDurableRunStore;
import com.ai.agent.durable.AgentDurableRuntimeProperties;
import com.ai.agent.durable.AgentRunTransition;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.runtime.AgentRunTerminationReason;
import com.ai.agent.tool.AgentConversationRecorder;
import com.ai.agent.tool.governance.AgentToolDescriptor;
import com.ai.agent.tool.governance.AgentToolRegistry;
import com.ai.security.SecurityConstants;
import com.ai.security.SecurityContextHelper;
import com.ai.security.SysUser;
import com.ai.security.SysUserRepository;
import com.ai.security.rbac.RolePermissionService;
import com.ai.conversation.ConversationAccessGuard;
import com.ai.background.BackgroundTaskConfiguration;
import com.ai.background.TaskSignalPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 审批查询、不可变决定和过期联动的安全边界。
 *
 * @author data-agent
 */
@Service
public class AgentApprovalService {

    private static final String NOT_FOUND_OR_DENIED = "审批不存在或无权限";

    private final AgentToolApprovalRepository approvalRepository;
    private final AgentDurableRunStore runStore;
    private final AgentDurableRuntimeProperties properties;
    private final AgentToolRegistry toolRegistry;
    private final SysUserRepository userRepository;
    private final RolePermissionService rolePermissionService;
    private final SecurityContextHelper securityContextHelper;
    private final AgentApprovalTelemetry telemetry;
    private final AgentConversationRecorder conversationRecorder;
    private final ConversationAccessGuard conversationAccess;
    private final ApprovalEventPublisher approvalEvents;
    private final TaskSignalPublisher taskSignals;

    public AgentApprovalService(AgentToolApprovalRepository approvalRepository,
            AgentDurableRunStore runStore,
            AgentDurableRuntimeProperties properties,
            AgentToolRegistry toolRegistry,
            SysUserRepository userRepository,
            RolePermissionService rolePermissionService,
            SecurityContextHelper securityContextHelper,
            AgentApprovalTelemetry telemetry,
            AgentConversationRecorder conversationRecorder, ConversationAccessGuard conversationAccess,
            ApprovalEventPublisher approvalEvents, TaskSignalPublisher taskSignals) {
        this.approvalRepository = approvalRepository;
        this.runStore = runStore;
        this.properties = properties;
        this.toolRegistry = toolRegistry;
        this.userRepository = userRepository;
        this.rolePermissionService = rolePermissionService;
        this.securityContextHelper = securityContextHelper;
        this.telemetry = telemetry;
        this.conversationRecorder = conversationRecorder;
        this.conversationAccess = conversationAccess;
        this.approvalEvents = approvalEvents;
        this.taskSignals = taskSignals;
    }

    @Transactional(readOnly = true)
    public ListPageResponse<AgentApprovalResponse> list(AgentApprovalDecisionStatus status, int page, int size) {
        SysUser reviewer = requireReviewer();
        ListPaging.page(page, size);
        var approvals = ListPageResponse.fetch(page, size,
                approvalRepository.countList(reviewer.getTenantId(), status),
                pageable -> approvalRepository.findListPage(reviewer.getTenantId(), status, pageable));
        Map<String, UserIdentity> identities = loadUserIdentities(reviewer.getTenantId(), approvals.items());
        return approvals.map(approval -> toResponse(approval, identities));
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
        var run = runStore.find(approval.getRunId())
                .orElseThrow(() -> new IllegalArgumentException("关联运行不存在"));
        if (!conversationAccess.allowContentWrite(run.getSessionId(), run.getTenantId(), run.getUserId())) {
            throw new IllegalArgumentException("所属会话已删除，审批不能继续");
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
        if (decision == AgentApprovalDecision.APPROVE) {
            approvalEvents.changed(run);
            taskSignals.afterCommit(BackgroundTaskConfiguration.RESUME_QUEUE, approvalId);
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
            updateConversation(approval.getRunId(), "审批已被拒绝，工具操作未执行。");
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
        var run = runStore.find(approval.getRunId()).orElse(null);
        if (run == null || !conversationAccess.allowContentWrite(run.getSessionId(), run.getTenantId(), run.getUserId())) {
            return false;
        }
        int changed = approvalRepository.expire(approval.getApprovalId(), now, approval.getVersion());
        if (changed != 1) {
            return false;
        }
        boolean transitioned = runStore.transition(
                approval.getRunId(),
                new AgentRunTransition(
                        AgentRunStatus.WAITING_APPROVAL,
                        AgentRunStatus.EXPIRED,
                        AgentRunTerminationReason.APPROVAL_EXPIRED,
                        "工具动作审批已过期"));
        if (transitioned) {
            updateConversation(approval.getRunId(), "审批已过期，工具操作未执行。");
        } else {
            // 审批记录已变化时也刷新管理页，不能依赖 Run 必须发生第二次转换。
            approvalEvents.changed(run);
        }
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

    private void updateConversation(String runId, String content) {
        conversationRecorder.updateRunConversation(runId, content, null, null);
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
        return toResponse(approval, loadUserIdentities(approval.getTenantId(), List.of(approval)));
    }

    private AgentApprovalResponse toResponse(AgentToolApprovalEntity approval,
            Map<String, UserIdentity> userIdentities) {
        UserIdentity requester = identityOf(userIdentities, approval.getRequesterUserId());
        UserIdentity reviewer = identityOf(userIdentities, approval.getReviewerUserId());
        return new AgentApprovalResponse(
                approval.getApprovalId(),
                approval.getRunId(),
                approval.getToolCallId(),
                approval.getToolName(),
                approval.getRiskLevel().name(),
                approval.getRequesterUserId(),
                requester.username(),
                requester.nickname(),
                approval.getSafeArgumentSummary(),
                approval.getDecisionStatus().name(),
                approval.getExecutionStatus().name(),
                approval.getReviewerUserId(),
                reviewer.username(),
                reviewer.nickname(),
                approval.getDecisionComment(),
                approval.getRequestedAt(),
                approval.getExpiresAt(),
                approval.getDecidedAt(),
                approval.getExecutionStartedAt(),
                approval.getExecutionCompletedAt());
    }

    /**
     * 一次查询解析审批列表涉及的用户身份，避免按审批记录逐条查询用户表。
     */
    private Map<String, UserIdentity> loadUserIdentities(String tenantId,
            List<AgentToolApprovalEntity> approvals) {
        Set<String> userIds = new LinkedHashSet<>();
        for (AgentToolApprovalEntity approval : approvals) {
            addUserId(userIds, approval.getRequesterUserId());
            addUserId(userIds, approval.getReviewerUserId());
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<String, UserIdentity> identities = new LinkedHashMap<>();
        userRepository.findIdentitiesByIds(userIds, tenantId)
                .forEach(user -> identities.put(user.userId(),
                        new UserIdentity(user.username(), user.nickname())));
        return Map.copyOf(identities);
    }

    private void addUserId(Set<String> userIds, String userId) {
        if (userId != null && !userId.isBlank()) {
            userIds.add(userId);
        }
    }

    private UserIdentity identityOf(Map<String, UserIdentity> userIdentities, String userId) {
        if (userId == null || userId.isBlank()) {
            return UserIdentity.UNKNOWN;
        }
        return userIdentities.getOrDefault(userId, UserIdentity.UNKNOWN);
    }

    /** 审批页面所需的最小用户身份投影。 */
    private record UserIdentity(String username, String nickname) {

        private static final UserIdentity UNKNOWN = new UserIdentity(null, null);
    }
}
