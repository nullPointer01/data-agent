package com.ai.security.rbac;
import com.ai.security.rbac.SysPermissionRepository;
import com.ai.security.rbac.SysPermission;
import com.ai.security.rbac.SysRoleRepository;
import com.ai.security.rbac.SysRole;

import com.ai.security.dto.PermissionResponse;
import com.ai.security.dto.RoleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .map(this::toRoleResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAll().stream()
                .map(this::toPermissionResponse)
                .toList();
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
