package com.ai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(SysUserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public Map<String, Object> register(String username, String password, String nickname,
                                         String email, String tenantId) {
        if (userRepository.existsByUsername(username)) {
            return Map.of("success", false, "message", "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname != null ? nickname : username);
        user.setEmail(email);
        user.setTenantId(tenantId != null ? tenantId : "default");
        user.setEnabled(true);
        user.setRoles(Set.of("USER"));

        userRepository.save(user);
        log.info("User registered: {}, tenant: {}", username, user.getTenantId());

        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getTenantId(), user.getRoles());
        String refreshToken = jwtTokenProvider.createRefreshToken(
                user.getId(), user.getUsername(), user.getTenantId());

        return Map.of(
                "success", true,
                "message", "注册成功",
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "userId", user.getId(),
                "username", user.getUsername(),
                "tenantId", user.getTenantId()
        );
    }

    public Map<String, Object> login(String username, String password) {
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return Map.of("success", false, "message", "用户名或密码错误");
        }

        SysUser user = userOpt.get();
        if (!user.isEnabled()) {
            return Map.of("success", false, "message", "账号已被禁用");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return Map.of("success", false, "message", "用户名或密码错误");
        }

        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getTenantId(), user.getRoles());
        String refreshToken = jwtTokenProvider.createRefreshToken(
                user.getId(), user.getUsername(), user.getTenantId());

        log.info("User logged in: {}, tenant: {}", username, user.getTenantId());

        return Map.of(
                "success", true,
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "userId", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "tenantId", user.getTenantId(),
                "roles", user.getRoles()
        );
    }

    public Map<String, Object> refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return Map.of("success", false, "message", "刷新令牌无效或已过期");
        }

        String userId = jwtTokenProvider.getUserId(refreshToken);
        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return Map.of("success", false, "message", "用户不存在");
        }

        SysUser user = userOpt.get();
        String newAccessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getTenantId(), user.getRoles());

        return Map.of(
                "success", true,
                "accessToken", newAccessToken
        );
    }

    @Transactional
    public Map<String, Object> assignRole(String userId, String role) {
        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return Map.of("success", false, "message", "用户不存在");
        }
        SysUser user = userOpt.get();
        user.getRoles().add(role);
        userRepository.save(user);
        log.info("Role {} assigned to user {}", role, user.getUsername());
        return Map.of("success", true, "message", "角色分配成功");
    }
}
