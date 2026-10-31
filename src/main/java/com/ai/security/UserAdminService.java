package com.ai.security;
import com.ai.security.auth.AuthResult;
import com.ai.security.rbac.RolePermissionService;

import com.ai.security.dto.AdminCreateUserRequest;
import com.ai.security.dto.AdminResetPasswordRequest;
import com.ai.security.dto.AdminUpdateUserRequest;
import com.ai.security.dto.AdminUpdateUserRolesRequest;
import com.ai.security.dto.UserAdminResponse;
import com.ai.security.dto.UserListResponse;
import com.ai.security.dto.UserIdentityResponse;
import com.ai.api.ListPaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 管理员用户管理服务。
 *
 * @author data-agent
 */
@Service
public class UserAdminService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserAdminService.class);

    private final SysUserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final RolePermissionService rolePermissionService;

    public UserAdminService(SysUserRepository userRepository, PasswordEncoder passwordEncoder,
            RolePermissionService rolePermissionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rolePermissionService = rolePermissionService;
    }

    /** 管理员平台级账号列表；保留既有跨租户管理范围，搜索与统计均在主库执行。 */
    @Transactional(readOnly = true)
    public UserListResponse listUsers(int page, int size, String query) {
        var request = ListPaging.page(page, size);
        String keyword = ListPaging.keyword(query);
        var counts = userRepository.countAdminUsers(keyword);
        int lastPage = (int) Math.max(0, (counts.total() - 1) / request.getPageSize());
        request = ListPaging.page(Math.min(page, lastPage), size);
        var items = userRepository.findAdminPage(keyword, request).stream().map(this::toResponse).toList();
        return new UserListResponse(true, items, request.getPageNumber(), size,
                counts.total(), counts.enabledCount(), counts.adminCount());
    }

    /** 仅解析页面已有资源的归属人，避免为显示用户名下载全部账号。 */
    @Transactional(readOnly = true)
    public List<UserIdentityResponse> userIdentities(List<String> ids) {
        if (ids == null || ids.size() > 100) throw new IllegalArgumentException("每次最多解析 100 个用户");
        if (ids.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 64)) {
            throw new IllegalArgumentException("用户 ID 无效");
        }
        return userRepository.findIdentitiesByIds(ids.stream().distinct().toList(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResult<UserAdminResponse> createUser(AdminCreateUserRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            return AuthResult.failure("用户名已存在", "USERNAME_EXISTS");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(defaultIfBlank(request.nickname(), username));
        user.setEmail(trimToNull(request.email()));
        user.setTenantId(defaultIfBlank(request.tenantId(), SecurityConstants.DEFAULT_TENANT_ID));
        user.setEnabled(request.enabled() == null || request.enabled());
        user.setDailyTokenLimit(request.dailyTokenLimit());
        user.setRoles(rolePermissionService.resolveRoles(request.roles()));

        SysUser savedUser = userRepository.save(user);
        LOGGER.info("Admin created user, username={}, tenantId={}", savedUser.getUsername(), savedUser.getTenantId());
        return AuthResult.success(toResponse(savedUser));
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResult<UserAdminResponse> updateUser(String userId, AdminUpdateUserRequest request) {
        Optional<SysUser> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return AuthResult.failure("用户不存在", "USER_NOT_FOUND");
        }

        SysUser user = userOptional.get();
        if (request.nickname() != null) {
            user.setNickname(defaultIfBlank(request.nickname(), user.getUsername()));
        }
        if (request.email() != null) {
            user.setEmail(trimToNull(request.email()));
        }
        if (request.tenantId() != null) {
            user.setTenantId(defaultIfBlank(request.tenantId(), SecurityConstants.DEFAULT_TENANT_ID));
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.dailyTokenLimit() != null) {
            user.setDailyTokenLimit(request.dailyTokenLimit());
        }

        SysUser savedUser = userRepository.save(user);
        LOGGER.info("Admin updated user, userId={}, username={}", userId, savedUser.getUsername());
        return AuthResult.success(toResponse(savedUser));
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResult<UserAdminResponse> updateRoles(String userId, AdminUpdateUserRolesRequest request) {
        Optional<SysUser> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return AuthResult.failure("用户不存在", "USER_NOT_FOUND");
        }

        SysUser user = userOptional.get();
        user.setRoles(rolePermissionService.resolveRoles(request.roles()));
        SysUser savedUser = userRepository.save(user);
        LOGGER.info("Admin updated user roles, userId={}, roles={}",
                userId, rolePermissionService.toRoleCodes(savedUser.getRoles()));
        return AuthResult.success(toResponse(savedUser));
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResult<UserAdminResponse> resetPassword(String userId, AdminResetPasswordRequest request) {
        Optional<SysUser> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return AuthResult.failure("用户不存在", "USER_NOT_FOUND");
        }

        SysUser user = userOptional.get();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        SysUser savedUser = userRepository.save(user);
        LOGGER.info("Admin reset user password, userId={}, username={}", userId, savedUser.getUsername());
        return AuthResult.success(toResponse(savedUser));
    }

    private UserAdminResponse toResponse(SysUser user) {
        return new UserAdminResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getTenantId(),
                user.isEnabled(),
                user.getDailyTokenLimit(),
                rolePermissionService.toRoleCodes(user.getRoles()),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
