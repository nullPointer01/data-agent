package com.ai.security.rbac;
import com.ai.security.rbac.RbacAdminService;

import com.ai.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * RBAC 管理接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/admin/rbac")
@PreAuthorize("hasRole(T(com.ai.security.SecurityConstants).ROLE_ADMIN)")
public class RbacAdminController {

    private final RbacAdminService rbacAdminService;

    public RbacAdminController(RbacAdminService rbacAdminService) {
        this.rbacAdminService = rbacAdminService;
    }

    /**
     * 查询角色列表。
     *
     * @return 角色列表
     */
    @GetMapping("/roles")
    public Map<String, Object> listRoles() {
        Map<String, Object> response = ApiResponse.success();
        response.put("roles", rbacAdminService.listRoles());
        return response;
    }

    /**
     * 查询权限列表。
     *
     * @return 权限列表
     */
    @GetMapping("/permissions")
    public Map<String, Object> listPermissions() {
        Map<String, Object> response = ApiResponse.success();
        response.put("permissions", rbacAdminService.listPermissions());
        return response;
    }
}
