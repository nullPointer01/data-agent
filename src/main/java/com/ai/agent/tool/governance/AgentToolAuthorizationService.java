package com.ai.agent.tool.governance;

import com.ai.security.SecurityConstants;
import com.ai.security.SysUser;
import com.ai.security.SysUserRepository;
import com.ai.security.rbac.RolePermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

/**
 * 从持久化 RBAC 数据解析一次 Run 的工具授权快照。
 *
 * @author data-agent
 */
@Service
public class AgentToolAuthorizationService {

    private final SysUserRepository userRepository;
    private final RolePermissionService rolePermissionService;
    private final AgentToolRegistry toolRegistry;

    public AgentToolAuthorizationService(SysUserRepository userRepository,
            RolePermissionService rolePermissionService,
            AgentToolRegistry toolRegistry) {
        this.userRepository = userRepository;
        this.rolePermissionService = rolePermissionService;
        this.toolRegistry = toolRegistry;
    }

    @Transactional(readOnly = true)
    public AgentToolAuthorizationSnapshot resolve(String userId, String tenantId) {
        if (userId == null || userId.isBlank() || tenantId == null || tenantId.isBlank()) {
            return AgentToolAuthorizationSnapshot.denied(userId, tenantId);
        }
        SysUser user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isEnabled() || !Objects.equals(tenantId, user.getTenantId())) {
            return AgentToolAuthorizationSnapshot.denied(userId, tenantId);
        }
        Set<String> permissions = rolePermissionService.toPermissionCodes(user.getRoles());
        boolean administrator = rolePermissionService.hasRole(user.getRoles(), SecurityConstants.ROLE_ADMIN)
                || permissions.contains("*:*");
        return new AgentToolAuthorizationSnapshot(userId, tenantId, permissions,
                toolRegistry.enabledToolNames(), true, administrator);
    }
}
