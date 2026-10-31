package com.ai.security.rbac;

import com.ai.api.ListPageResponse;
import com.ai.api.ListPaging;
import com.ai.security.dto.PermissionResponse;
import com.ai.security.dto.RoleListResponse;
import com.ai.security.dto.RoleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC 管理查询服务。
 *
 * @author data-agent
 */
@Service
public class RbacAdminService {

    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;

    public RbacAdminService(SysRoleRepository roleRepository, SysPermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public RoleListResponse listRoles(int page, int size, String query) {
        ListPaging.page(page, size);
        String keyword = ListPaging.keyword(query);
        var counts = roleRepository.countList(keyword);
        var result = ListPageResponse.fetch(page, size, counts.total(),
                pageable -> roleRepository.findListPage(keyword, pageable)).map(this::toRoleResponse);
        return new RoleListResponse(true, result.items(), result.page(), result.size(), result.total(),
                counts.enabledCount(), counts.systemCount());
    }

    @Transactional(readOnly = true)
    public ListPageResponse<PermissionResponse> listPermissions(int page, int size, String query) {
        ListPaging.page(page, size);
        String keyword = ListPaging.keyword(query);
        return ListPageResponse.fetch(page, size, permissionRepository.countList(keyword),
                pageable -> permissionRepository.findListPage(keyword, pageable)).map(this::toPermissionResponse);
    }

    private RoleResponse toRoleResponse(SysRole role) {
        Set<String> permissions = role.getPermissions().stream()
                .map(SysPermission::getPermissionCode)
                .sorted()
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return new RoleResponse(
                role.getRoleCode(),
                role.getName(),
                role.getDescription(),
                role.isSystemRole(),
                role.isEnabled(),
                permissions,
                role.getCreatedAt(),
                role.getUpdatedAt());
    }

    private PermissionResponse toPermissionResponse(SysPermission permission) {
        return new PermissionResponse(
                permission.getPermissionCode(),
                permission.getName(),
                permission.getDescription(),
                permission.getResourceType(),
                permission.getAction(),
                permission.isEnabled(),
                permission.getCreatedAt(),
                permission.getUpdatedAt());
    }
}
