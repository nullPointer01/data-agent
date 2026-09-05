package com.ai.agent.tool.governance;

import java.util.Set;

/**
 * Agent Run 创建时固化的不可变工具授权事实。
 *
 * @param userId 用户编号
 * @param tenantId 租户编号
 * @param permissionCodes 已启用角色对应的权限码
 * @param serverEnabledTools 服务端启用的工具集合
 * @param authenticated 主体是否通过持久化用户和租户校验
 * @param administrator 是否具有管理员或全局权限
 * @author data-agent
 */
public record AgentToolAuthorizationSnapshot(
        String userId,
        String tenantId,
        Set<String> permissionCodes,
        Set<String> serverEnabledTools,
        boolean authenticated,
        boolean administrator) {

    public AgentToolAuthorizationSnapshot {
        permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
        serverEnabledTools = serverEnabledTools == null ? Set.of() : Set.copyOf(serverEnabledTools);
    }

    public boolean permits(String toolName, String requiredPermission) {
        if (!authenticated || !serverEnabledTools.contains(toolName)) {
            return false;
        }
        return administrator
                || permissionCodes.contains("*:*")
                || permissionCodes.contains(requiredPermission);
    }

    public static AgentToolAuthorizationSnapshot denied(String userId, String tenantId) {
        return new AgentToolAuthorizationSnapshot(userId, tenantId, Set.of(), Set.of(), false, false);
    }
}
