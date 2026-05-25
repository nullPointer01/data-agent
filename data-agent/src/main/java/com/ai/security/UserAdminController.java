package com.ai.security;
import com.ai.security.auth.AuthResult;

import com.ai.api.ApiResponse;
import com.ai.security.dto.AdminCreateUserRequest;
import com.ai.security.dto.AdminResetPasswordRequest;
import com.ai.security.dto.AdminUpdateUserRequest;
import com.ai.security.dto.AdminUpdateUserRolesRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Administrative user management API.
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole(T(com.ai.security.SecurityConstants).ROLE_ADMIN)")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listUsers() {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.listUsers()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return toResponse(userAdminService.createUser(request), HttpStatus.CREATED);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        return toResponse(userAdminService.updateUser(userId, request), HttpStatus.OK);
    }

    @PutMapping("/{userId}/roles")
    public ResponseEntity<Map<String, Object>> updateRoles(
            @PathVariable String userId,
            @Valid @RequestBody AdminUpdateUserRolesRequest request) {
        return toResponse(userAdminService.updateRoles(userId, request), HttpStatus.OK);
    }

    @PatchMapping("/{userId}/password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @PathVariable String userId,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        return toResponse(userAdminService.resetPassword(userId, request), HttpStatus.OK);
    }

    private ResponseEntity<Map<String, Object>> toResponse(AuthResult<?> result, HttpStatus successStatus) {
        if (result.success()) {
            return ResponseEntity.status(successStatus).body(ApiResponse.success(result.data()));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(result.message(), result.code()));
    }
}
