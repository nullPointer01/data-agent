package com.ai.security.auth;
import com.ai.security.auth.AuthService;
import com.ai.security.auth.AuthResult;

import com.ai.api.ApiResponse;
import com.ai.security.dto.BootstrapAdminRequest;
import com.ai.security.dto.LoginRequest;
import com.ai.security.dto.RefreshTokenRequest;
import com.ai.security.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证 API。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 按系统注册策略创建普通用户。
     *
     * @param request 注册资料和登录密码
     * @return 新用户登录令牌或注册失败信息
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return toResponse(authService.register(request), HttpStatus.CREATED);
    }

    /**
     * 在系统尚无管理员时创建或提权首个管理员。
     *
     * @param request 管理员账号资料
     * @return 管理员登录令牌或初始化失败信息
     */
    @PostMapping("/bootstrap-admin")
    public ResponseEntity<Map<String, Object>> bootstrapAdmin(@Valid @RequestBody BootstrapAdminRequest request) {
        return toResponse(authService.bootstrapAdmin(request), HttpStatus.CREATED);
    }

    /**
     * 校验账号密码并签发访问令牌和刷新令牌。
     *
     * @param request 用户名和密码
     * @return 登录令牌和用户信息，校验失败时返回错误
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return toResponse(authService.login(request), HttpStatus.OK);
    }

    /**
     * 校验刷新令牌并签发新的访问令牌。
     *
     * @param request 刷新令牌
     * @return 新访问令牌或校验失败信息
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return toResponse(authService.refreshToken(request), HttpStatus.OK);
    }

    private ResponseEntity<Map<String, Object>> toResponse(AuthResult<?> result, HttpStatus successStatus) {
        if (result.success()) {
            return ResponseEntity.status(successStatus).body(ApiResponse.success(result.data()));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(result.message(), result.code()));
    }
}
