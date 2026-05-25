package com.ai.security.rbac;
import com.ai.security.rbac.SysPermission;
import com.ai.security.rbac.SysRoleRepository;
import com.ai.security.rbac.SysRole;
import com.ai.security.SecurityConstants;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色和权限的领域服务。
 *
 * @author data-agent
 */
@Service
public class RolePermissionService {

    private final SysRoleRepository roleRepository;

    public RolePermissionService(SysRoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public Set<SysRole> resolveRoles(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of(requireRole(SecurityConstants.ROLE_USER));
        }

        Set<String> normalizedCodes = roleCodes.stream()
                .map(this::normalizeRoleCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<SysRole> roles = roleRepository.findByRoleCodeIn(normalizedCodes);
        Set<String> foundCodes = roles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toSet());
        for (String roleCode : normalizedCodes) {
            if (!foundCodes.contains(roleCode)) {
                throw new IllegalArgumentException("角色不存在: " + roleCode);
            }
        }
        return new LinkedHashSet<>(roles);
    }

    @Transactional(readOnly = true)
    public SysRole requireRole(String roleCode) {
        return roleRepository.findById(normalizeRoleCode(roleCode))
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + roleCode));
    }

    public Set<String> toRoleCodes(Set<SysRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        return roles.stream()
                .filter(SysRole::isEnabled)
                .map(SysRole::getRoleCode)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> toPermissionCodes(Set<SysRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        return roles.stream()
                .filter(SysRole::isEnabled)
                .flatMap(role -> role.getPermissions().stream())
                .filter(SysPermission::isEnabled)
                .map(SysPermission::getPermissionCode)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean hasRole(Set<SysRole> roles, String roleCode) {
        String normalizedRoleCode = normalizeRoleCode(roleCode);
        return roles != null && roles.stream()
                .anyMatch(role -> role.isEnabled() && role.getRoleCode().equals(normalizedRoleCode));
    }

    @Transactional(readOnly = true)
    public List<SysRole> listRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(SysRole::getRoleCode))
                .toList();
    }

    private String normalizeRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("角色不能为空");
        }
        return roleCode.trim().toUpperCase();
    }
}
