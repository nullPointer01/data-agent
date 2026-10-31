package com.ai.background;

import com.ai.config.TaskContextSnapshot;
import com.ai.security.SecurityConstants;
import com.ai.security.SysUserRepository;
import com.ai.security.TenantUser;
import com.ai.security.rbac.RolePermissionService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

/** 后台模型调用重新加载启用用户及当前权限，不保存或重放登录令牌。 */
@Component
public class BackgroundUserScope {
    private final SysUserRepository users;
    private final RolePermissionService permissions;

    public BackgroundUserScope(SysUserRepository users, RolePermissionService permissions) {
        this.users = users;
        this.permissions = permissions;
    }

    /** 无效或禁用身份不再调用模型；任务中的租户必须仍与当前账号一致。 */
    public <T> T call(String tenant, String userId, Supplier<T> action) {
        var user = users.findById(userId).filter(value -> value.isEnabled() && tenant.equals(value.getTenantId()))
                .orElseThrow(() -> new SecurityException("后台任务用户已失效"));
        if (!permissions.hasPermission(user.getRoles(), "app:use")) {
            throw new SecurityException("后台任务用户已无应用权限");
        }
        var authorities = permissions.toRoleCodes(user.getRoles()).stream()
                .map(role -> new SimpleGrantedAuthority(SecurityConstants.ROLE_PREFIX + role)).toList();
        var principal = new TenantUser(user.getId(), user.getUsername(), tenant, authorities);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        return new TaskContextSnapshot(Map.of("tenantId", tenant, "userId", userId),
                authentication, null, null, null).call(action);
    }
}
