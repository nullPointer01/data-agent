package com.ai.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 系统用户仓储。
 *
 * @author data-agent
 */
public interface SysUserRepository extends JpaRepository<SysUser, String> {

    /**
     * 按用户名查找用户。
     *
     * @param username 用户名
     * @return 匹配的用户
     */
    Optional<SysUser> findByUsername(String username);

    /**
     * 检查用户名是否已存在。
     *
     * @param username 用户名
     * @return 存在时返回 true
     */
    boolean existsByUsername(String username);

    /**
     * 判断指定角色是否已存在。
     *
     * @param role 角色
     * @return true 表示已有用户持有该角色
     */
    boolean existsByRolesRoleCode(String role);
}
