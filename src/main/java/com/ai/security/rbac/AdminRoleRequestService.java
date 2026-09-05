package com.ai.security.rbac;
import com.ai.security.rbac.AdminRoleRequestStatus;
import com.ai.security.rbac.AdminRoleRequestRepository;
import com.ai.security.rbac.AdminRoleRequest;
import com.ai.security.rbac.RolePermissionService;
import com.ai.security.SecurityConstants;
import com.ai.security.TenantUser;
import com.ai.security.SecurityContextHelper;
import com.ai.security.SysUserRepository;
import com.ai.security.SysUser;

import com.ai.security.dto.AdminRoleRequestCreateRequest;
import com.ai.security.dto.AdminRoleRequestResponse;
import com.ai.security.dto.AdminRoleRequestReviewRequest;
import com.ai.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 管理员角色申请服务。
 *
 * @author data-agent
 */
@Service
public class AdminRoleRequestService {

    private static final String AUDIT_RESOURCE_TYPE = "ADMIN_ROLE_REQUEST";
    private static final String AUDIT_SUBMITTED = "ADMIN_ROLE_REQUEST_SUBMITTED";
    private static final String AUDIT_APPROVED = "ADMIN_ROLE_REQUEST_APPROVED";
    private static final String AUDIT_REJECTED = "ADMIN_ROLE_REQUEST_REJECTED";
    private static final String STATUS_SUCCESS = "SUCCESS";

    private final AdminRoleRequestRepository requestRepository;
    private final SysUserRepository userRepository;
    private final SecurityContextHelper securityContextHelper;
    private final AuditLogService auditLogService;

    private final RolePermissionService rolePermissionService;

    public AdminRoleRequestService(AdminRoleRequestRepository requestRepository,
            SysUserRepository userRepository,
            SecurityContextHelper securityContextHelper,
            AuditLogService auditLogService,
            RolePermissionService rolePermissionService) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.securityContextHelper = securityContextHelper;
        this.auditLogService = auditLogService;
        this.rolePermissionService = rolePermissionService;
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminRoleRequestResponse submit(AdminRoleRequestCreateRequest request) {
        TenantUser currentUser = requireCurrentUser();
        SysUser user = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"));
        if (rolePermissionService.hasRole(user.getRoles(), SecurityConstants.ROLE_ADMIN)) {
            throw new IllegalArgumentException("当前账号已经是管理员，无需重复申请");
        }
        requestRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(
                user.getId(), AdminRoleRequestStatus.PENDING).ifPresent(item -> {
                    throw new IllegalArgumentException("已有待审核的管理员申请，请等待处理");
                });

        AdminRoleRequest entity = new AdminRoleRequest();
        entity.setUserId(user.getId());
        entity.setUsername(user.getUsername());
        entity.setTenantId(resolveTenantId(user.getTenantId()));
        entity.setReason(request.reason().trim());
        entity.setStatus(AdminRoleRequestStatus.PENDING);

        AdminRoleRequest saved = requestRepository.save(entity);
        auditLogService.record(AUDIT_SUBMITTED, AUDIT_RESOURCE_TYPE, saved.getRequestId(), STATUS_SUCCESS,
                "用户提交管理员角色申请");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AdminRoleRequestResponse> listMine() {
        TenantUser currentUser = requireCurrentUser();
        return requestRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminRoleRequestResponse> listForReview(AdminRoleRequestStatus status) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<AdminRoleRequest> requests = status == null
                ? requestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : requestRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        return requests.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminRoleRequestResponse approve(String requestId, AdminRoleRequestReviewRequest request) {
        AdminRoleRequest roleRequest = loadPendingRequest(requestId);
        TenantUser reviewer = requireCurrentUser();
        SysUser user = userRepository.findById(roleRequest.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("申请用户不存在"));
        user.setRoles(rolePermissionService.resolveRoles(Set.of(
                SecurityConstants.ROLE_USER, SecurityConstants.ROLE_ADMIN)));
        userRepository.save(user);

        finishReview(roleRequest, AdminRoleRequestStatus.APPROVED, reviewer, request.comment());
        AdminRoleRequest saved = requestRepository.save(roleRequest);
        auditLogService.record(AUDIT_APPROVED, AUDIT_RESOURCE_TYPE, requestId, STATUS_SUCCESS,
                "管理员申请已通过，目标用户: " + roleRequest.getUsername());
        return toResponse(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminRoleRequestResponse reject(String requestId, AdminRoleRequestReviewRequest request) {
        AdminRoleRequest roleRequest = loadPendingRequest(requestId);
        TenantUser reviewer = requireCurrentUser();
        finishReview(roleRequest, AdminRoleRequestStatus.REJECTED, reviewer, request.comment());
        AdminRoleRequest saved = requestRepository.save(roleRequest);
        auditLogService.record(AUDIT_REJECTED, AUDIT_RESOURCE_TYPE, requestId, STATUS_SUCCESS,
                "管理员申请已拒绝，目标用户: " + roleRequest.getUsername());
        return toResponse(saved);
    }

    private AdminRoleRequest loadPendingRequest(String requestId) {
        AdminRoleRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("管理员申请不存在"));
        String tenantId = securityContextHelper.getCurrentTenantId();
        if (!request.getTenantId().equals(tenantId)) {
            throw new SecurityException("不能审核其他租户的管理员申请");
        }
        if (request.getStatus() != AdminRoleRequestStatus.PENDING) {
            throw new IllegalArgumentException("该申请已处理，不能重复审核");
        }
        return request;
    }

    private void finishReview(AdminRoleRequest request, AdminRoleRequestStatus status,
            TenantUser reviewer, String comment) {
        request.setStatus(status);
        request.setReviewerId(reviewer.getUserId());
        request.setReviewerName(reviewer.getUsername());
        request.setReviewComment(StringUtils.hasText(comment) ? comment.trim() : null);
        request.setReviewedAt(LocalDateTime.now());
    }

    private TenantUser requireCurrentUser() {
        TenantUser currentUser = securityContextHelper.getCurrentUser();
        if (currentUser == null) {
            throw new SecurityException("当前用户未登录");
        }
        return currentUser;
    }

    private String resolveTenantId(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId : SecurityConstants.DEFAULT_TENANT_ID;
    }

    private AdminRoleRequestResponse toResponse(AdminRoleRequest request) {
        return new AdminRoleRequestResponse(
                request.getRequestId(),
                request.getUserId(),
                request.getUsername(),
                request.getTenantId(),
                request.getReason(),
                request.getStatus(),
                request.getReviewerId(),
                request.getReviewerName(),
                request.getReviewComment(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getReviewedAt());
    }
}
