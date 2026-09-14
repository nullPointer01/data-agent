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
 * 用户管理管理员 API。
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

    /**
     * 查询平台中的用户及其当前角色。
     *
     * @return 用户列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listUsers() {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.listUsers()));
    }

    /**
     * 由管理员创建用户并分配初始角色。
     *
     * @param request 用户资料、租户、角色和配额
     * @return 创建的用户信息或校验错误
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return toResponse(userAdminService.createUser(request), HttpStatus.CREATED);
    }

    /**
     * 由管理员更新指定用户的资料、状态或配额。
     *
     * @param userId 用户编号
     * @param request 可变更的用户资料
     * @return 更新后的用户信息或校验错误
     */
    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        return toResponse(userAdminService.updateUser(userId, request), HttpStatus.OK);
    }

    /**
     * 由管理员替换指定用户的角色集合。
     *
     * @param userId 用户编号
     * @param request 新的角色集合
     * @return 更新后的用户信息或校验错误
     */
    @PutMapping("/{userId}/roles")
    public ResponseEntity<Map<String, Object>> updateRoles(
            @PathVariable String userId,
            @Valid @RequestBody AdminUpdateUserRolesRequest request) {
        return toResponse(userAdminService.updateRoles(userId, request), HttpStatus.OK);
    }

    /**
     * 由管理员重置指定用户的登录密码。
     *
     * @param userId 用户编号
     * @param request 新密码
     * @return 密码重置结果
     */
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
