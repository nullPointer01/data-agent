package com.ai.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for system users.
 *
 * @author data-agent
 */
public interface SysUserRepository extends JpaRepository<SysUser, String> {

    /**
     * Finds a user by username.
     *
     * @param username username
     * @return matched user
     */
    Optional<SysUser> findByUsername(String username);

    /**
     * Checks whether the username exists.
     *
     * @param username username
     * @return true when exists
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
