package com.ai.security;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import com.ai.security.dto.UserIdentityResponse;
import com.ai.security.dto.UserListCounts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** SysUser 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface SysUserRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default SysUser save(SysUser entity) {
        boolean creating = entity.getId() == null || !existsById(entity.getId());
        if (creating) {
            entity.onCreate();
            insert(entity);
        } else {
            entity.onUpdate();
            if (update(entity) != 1) {
                throw new OptimisticLockingFailureException("记录已变化，请重新读取后保存");
            }
        }
        deleteRoleLinks(entity.getId());
        if (!entity.getRoles().isEmpty()) {
            insertRoleLinks(entity.getId(), entity.getRoles().stream().map(com.ai.security.rbac.SysRole::getRoleCode).toList());
        }
        return entity;
    }

    int insert(SysUser entity);
    int update(SysUser entity);
    Optional<SysUser> findById(@Param("id") String id);
    boolean existsById(@Param("id") String id);
    List<SysUser> findAll();
    /** 先对用户分页再关联角色，避免角色条数改变页大小或逐行查角色。 */
    List<SysUser> findAdminPage(@Param("query") String query, @Param("pageable") Pageable pageable);
    UserListCounts countAdminUsers(@Param("query") String query);
    List<UserIdentityResponse> findIdentitiesByIds(@Param("ids") Collection<String> ids,
            @Param("tenantId") String tenantId);
    List<SysUser> findAllById(@Param("ids") Collection<String> ids);
    void deleteById(@Param("id") String id);

    default void delete(SysUser entity) {
        deleteById(entity.getId());
    }

    @Transactional
    default List<SysUser> saveAll(Iterable<SysUser> entities) {
        List<SysUser> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    Optional<SysUser> findByUsername(@Param("username") String username);

    boolean existsByUsername(@Param("username") String username);

    boolean existsByRolesRoleCode(@Param("role") String role);

    void deleteRoleLinks(@Param("userId") String userId);
    void insertRoleLinks(@Param("userId") String userId, @Param("roleCodes") List<String> roleCodes);
}
