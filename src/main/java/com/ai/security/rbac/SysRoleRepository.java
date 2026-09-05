package com.ai.security.rbac;
import com.ai.security.rbac.SysRole;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 系统角色仓储。
 *
 * @author data-agent
 */
public interface SysRoleRepository extends JpaRepository<SysRole, String> {

    /**
     * 查询启用角色。
     *
     * @return 角色列表
     */
    List<SysRole> findByEnabledTrueOrderByRoleCodeAsc();

    /**
     * 按角色编码查询角色。
     *
     * @param roleCodes 角色编码
     * @return 角色列表
     */
    List<SysRole> findByRoleCodeIn(Collection<String> roleCodes);
}
