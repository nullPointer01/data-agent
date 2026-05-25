package com.ai.security.rbac;
import com.ai.security.rbac.SysPermission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 系统权限仓储。
 *
 * @author data-agent
 */
public interface SysPermissionRepository extends JpaRepository<SysPermission, String> {

    /**
     * 查询启用权限。
     *
     * @return 权限列表
     */
    List<SysPermission> findByEnabledTrueOrderByPermissionCodeAsc();

    /**
     * 按权限编码查询权限。
     *
     * @param permissionCodes 权限编码
     * @return 权限列表
     */
    List<SysPermission> findByPermissionCodeIn(Collection<String> permissionCodes);
}
