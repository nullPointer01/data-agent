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

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return toResponse(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/bootstrap-admin")
    public ResponseEntity<Map<String, Object>> bootstrapAdmin(@Valid @RequestBody BootstrapAdminRequest request) {
        return toResponse(authService.bootstrapAdmin(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return toResponse(authService.login(request), HttpStatus.OK);
    }

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
