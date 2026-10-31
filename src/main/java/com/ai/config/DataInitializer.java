package com.ai.config;

import com.ai.security.rbac.RolePermissionService;
import com.ai.security.SecurityConstants;
import com.ai.security.rbac.SysPermission;
import com.ai.security.rbac.SysPermissionRepository;
import com.ai.security.rbac.SysRole;
import com.ai.security.rbac.SysRoleRepository;
import com.ai.security.SysUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

/**
 * 初始化默认权限和角色；表结构与旧库升级统一由 Flyway 管理。
 *
 * @author data-agent
 */
@Configuration
public class DataInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataInitializer.class);
    private static final String USERNAME_DELIMITER = ",";
    private static final String PERMISSION_ALL = "*:*";
    private static final String PERMISSION_APP_USE = "app:use";
    private static final String PERMISSION_APPROVAL_REVIEW = SecurityConstants.PERMISSION_AGENT_APPROVAL_REVIEW;

    @Value("${app.security.bootstrap-admin-users:super}")
    private String bootstrapAdminUsers;

    @Bean
    public CommandLineRunner initData(SysUserRepository sysUserRepository,
            SysRoleRepository roleRepository,
            SysPermissionRepository permissionRepository,
            RolePermissionService rolePermissionService) {
        return args -> {
            initDefaultRbac(roleRepository, permissionRepository, rolePermissionService);
            bootstrapAdminUsers(sysUserRepository, rolePermissionService);
        };
    }

    private void initDefaultRbac(SysRoleRepository roleRepository,
            SysPermissionRepository permissionRepository,
            RolePermissionService rolePermissionService) {
        SysPermission appUse = savePermissionIfAbsent(permissionRepository, PERMISSION_APP_USE,
                "应用使用", "登录后使用普通功能", "APP", "USE");
        SysPermission all = savePermissionIfAbsent(permissionRepository, PERMISSION_ALL,
                "全部权限", "系统管理员拥有全部管理权限", "SYSTEM", "*");
        SysPermission approvalReview = savePermissionIfAbsent(
                permissionRepository,
                PERMISSION_APPROVAL_REVIEW,
                "Agent动作审批",
                "审核需要人工确认的Agent工具动作",
                "AGENT_APPROVAL",
                "REVIEW");

        saveRoleIfAbsent(roleRepository, SecurityConstants.ROLE_USER, "普通用户",
                "可使用对话、资料、知识库等基础功能", Set.of(appUse));
        saveRoleIfAbsent(roleRepository, SecurityConstants.ROLE_ADMIN, "系统管理员",
                "可管理用户、角色、模型、技能、Agent、数据源和审计", Set.of(appUse, all, approvalReview));
        rolePermissionService.ensureRolePermission(SecurityConstants.ROLE_ADMIN, approvalReview);
    }

    private SysPermission savePermissionIfAbsent(SysPermissionRepository repository, String code, String name,
            String description, String resourceType, String action) {
        return repository.findById(code).orElseGet(() -> {
            SysPermission permission = new SysPermission();
            permission.setPermissionCode(code);
            permission.setName(name);
            permission.setDescription(description);
            permission.setResourceType(resourceType);
            permission.setAction(action);
            permission.setEnabled(true);
            return repository.save(permission);
        });
    }

    private void saveRoleIfAbsent(SysRoleRepository repository, String code, String name, String description,
            Set<SysPermission> permissions) {
        repository.findById(code).orElseGet(() -> {
            SysRole role = new SysRole();
            role.setRoleCode(code);
            role.setName(name);
            role.setDescription(description);
            role.setSystemRole(true);
            role.setEnabled(true);
            role.setPermissions(permissions);
            return repository.save(role);
        });
    }

    private void bootstrapAdminUsers(SysUserRepository userRepository, RolePermissionService rolePermissionService) {
        if (bootstrapAdminUsers == null || bootstrapAdminUsers.isBlank()) {
            return;
        }

        for (String username : bootstrapAdminUsers.split(USERNAME_DELIMITER)) {
            String trimmedUsername = username.trim();
            if (trimmedUsername.isEmpty()) {
                continue;
            }
            userRepository.findByUsername(trimmedUsername).ifPresent(user -> {
                if (!rolePermissionService.hasRole(user.getRoles(), SecurityConstants.ROLE_ADMIN)) {
                    user.setRoles(Set.of(rolePermissionService.requireRole(SecurityConstants.ROLE_USER),
                            rolePermissionService.requireRole(SecurityConstants.ROLE_ADMIN)));
                    userRepository.save(user);
                    LOGGER.warn("Bootstrapped ADMIN role for user {}", trimmedUsername);
                }
            });
        }
    }

}
