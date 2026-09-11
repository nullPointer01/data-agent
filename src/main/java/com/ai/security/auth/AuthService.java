package com.ai.security.auth;
import com.ai.security.auth.JwtTokenProvider;
import com.ai.security.auth.AuthResult;
import com.ai.security.rbac.RolePermissionService;
import com.ai.security.SecurityConstants;
import com.ai.security.SysUserRepository;
import com.ai.security.SysUser;

import com.ai.security.dto.AccessTokenResponse;
import com.ai.security.dto.AuthResponse;
import com.ai.security.dto.BootstrapAdminRequest;
import com.ai.security.dto.LoginRequest;
import com.ai.security.dto.RefreshTokenRequest;
import com.ai.security.dto.RegisterRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.Set;

/**
 * 认证应用服务，包含注册、登录和令牌刷新功能。
 *
 * @author data-agent
 */
@Service
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final SysUserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider jwtTokenProvider;

    private final RolePermissionService rolePermissionService;

    private final boolean registrationEnabled;

    private final String registrationTenantId;

    public AuthService(SysUserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider, RolePermissionService rolePermissionService,
            @Value("${app.security.registration.enabled:false}") boolean registrationEnabled,
            @Value("${app.security.registration.tenant-id:default}") String registrationTenantId) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.rolePermissionService = rolePermissionService;
        this.registrationEnabled = registrationEnabled;
        this.registrationTenantId = registrationTenantId;
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResult<AuthResponse> register(RegisterRequest request) {
        if (!registrationEnabled) {
            return AuthResult.failure("注册功能未开放", "REGISTRATION_DISABLED");
        }
        if (!StringUtils.hasText(registrationTenantId)) {
            return AuthResult.failure("注册租户未配置", "REGISTRATION_TENANT_NOT_CONFIGURED");
        }
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            return AuthResult.failure("用户名已存在", "USERNAME_EXISTS");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(defaultIfBlank(request.nickname(), username));
        user.setEmail(trimToNull(request.email()));
        user.setTenantId(registrationTenantId.trim());
        user.setEnabled(true);
        user.setDailyTokenLimit(SecurityConstants.DEFAULT_USER_DAILY_TOKEN_LIMIT);
        user.setRoles(rolePermissionService.resolveRoles(Set.of(SecurityConstants.ROLE_USER)));

        userRepository.save(user);
        LOGGER.info("User registered, username={}, tenantId={}", username, user.getTenantId());

        return AuthResult.success(buildAuthResponse(user));
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResult<AuthResponse> bootstrapAdmin(BootstrapAdminRequest request) {
        if (userRepository.existsByRolesRoleCode(SecurityConstants.ROLE_ADMIN)) {
            return AuthResult.failure("系统已存在管理员，不能再次初始化", "ADMIN_ALREADY_EXISTS");
        }

        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            return promoteExistingUserToAdmin(request, username);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(defaultIfBlank(request.nickname(), username));
        user.setEmail(trimToNull(request.email()));
        user.setTenantId(defaultIfBlank(request.tenantId(), SecurityConstants.DEFAULT_TENANT_ID));
        user.setEnabled(true);
        user.setDailyTokenLimit(SecurityConstants.DEFAULT_USER_DAILY_TOKEN_LIMIT);
        user.setRoles(rolePermissionService.resolveRoles(Set.of(
                SecurityConstants.ROLE_USER, SecurityConstants.ROLE_ADMIN)));

        userRepository.save(user);
        LOGGER.warn("Bootstrap admin created, username={}, tenantId={}", username, user.getTenantId());

        return AuthResult.success(buildAuthResponse(user));
    }

    public AuthResult<AuthResponse> login(LoginRequest request) {
        String username = request.username().trim();
        Optional<SysUser> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return AuthResult.failure("用户名或密码错误", "INVALID_CREDENTIALS");
        }

        SysUser user = userOptional.get();
        if (!user.isEnabled()) {
            return AuthResult.failure("账号已被禁用", "USER_DISABLED");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            return AuthResult.failure("用户名或密码错误", "INVALID_CREDENTIALS");
        }

        LOGGER.info("User logged in, username={}, tenantId={}", username, user.getTenantId());

        return AuthResult.success(buildAuthResponse(user));
    }

    private AuthResult<AuthResponse> promoteExistingUserToAdmin(BootstrapAdminRequest request, String username) {
        Optional<SysUser> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return AuthResult.failure("用户不存在", "USER_NOT_FOUND");
        }

        SysUser user = userOptional.get();
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            return AuthResult.failure("用户名已存在，请输入该用户的正确密码后再初始化管理员", "INVALID_CREDENTIALS");
        }

        user.setRoles(rolePermissionService.resolveRoles(Set.of(
                SecurityConstants.ROLE_USER, SecurityConstants.ROLE_ADMIN)));
        user.setEnabled(true);
        SysUser savedUser = userRepository.save(user);
        LOGGER.warn("Bootstrap admin promoted existing user, username={}, tenantId={}",
                username, savedUser.getTenantId());
        return AuthResult.success(buildAuthResponse(savedUser));
    }

    public AuthResult<AccessTokenResponse> refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return AuthResult.failure("刷新令牌无效或已过期", "INVALID_REFRESH_TOKEN");
        }

        String userId = jwtTokenProvider.getUserId(refreshToken);
        Optional<SysUser> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return AuthResult.failure("用户不存在", "USER_NOT_FOUND");
        }

        SysUser user = userOptional.get();
        if (!user.isEnabled()) {
            return AuthResult.failure("账号已被禁用", "USER_DISABLED");
        }
        String newAccessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getTenantId(),
                rolePermissionService.toRoleCodes(user.getRoles()));

        return AuthResult.success(new AccessTokenResponse(newAccessToken));
    }

    private AuthResponse buildAuthResponse(SysUser user) {
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getTenantId(),
                rolePermissionService.toRoleCodes(user.getRoles()));
        String refreshToken = jwtTokenProvider.createRefreshToken(
                user.getId(), user.getUsername(), user.getTenantId());
        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getTenantId(),
                rolePermissionService.toRoleCodes(user.getRoles()));
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
