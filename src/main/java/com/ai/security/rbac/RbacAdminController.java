package com.ai.security.rbac;
import com.ai.api.ListPageResponse;
import com.ai.security.dto.PermissionResponse;
import com.ai.security.dto.RoleListResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public RoleListResponse listRoles(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String query) {
        return rbacAdminService.listRoles(page, size, query);
    }

    /**
     * 查询权限列表。
     *
     * @return 权限列表
     */
    @GetMapping("/permissions")
    public ListPageResponse<PermissionResponse> listPermissions(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String query) {
        return rbacAdminService.listPermissions(page, size, query);
    }
}
