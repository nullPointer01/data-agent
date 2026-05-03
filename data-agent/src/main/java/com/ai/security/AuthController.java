package com.ai.security;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String nickname = body.get("nickname");
        String email = body.get("email");
        String tenantId = body.get("tenantId");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return Map.of("success", false, "message", "用户名和密码不能为空");
        }
        if (password.length() < 6) {
            return Map.of("success", false, "message", "密码长度不能少于6位");
        }
        return authService.register(username.trim(), password, nickname, email, tenantId);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return Map.of("success", false, "message", "用户名和密码不能为空");
        }
        return authService.login(username, password);
    }

    @PostMapping("/refresh")
    public Map<String, Object> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null) {
            return Map.of("success", false, "message", "刷新令牌不能为空");
        }
        return authService.refreshToken(refreshToken);
    }

    @PostMapping("/assign-role")
    public Map<String, Object> assignRole(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String role = body.get("role");
        if (userId == null || role == null) {
            return Map.of("success", false, "message", "参数不完整");
        }
        return authService.assignRole(userId, role);
    }
}
