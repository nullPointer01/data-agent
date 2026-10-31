package com.ai.security.rbac;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** SysRole 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface SysRoleRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default SysRole save(SysRole entity) {
        boolean creating = entity.getRoleCode() == null || !existsById(entity.getRoleCode());
        if (creating) {
            entity.onCreate();
            insert(entity);
        } else {
            entity.onUpdate();
            if (update(entity) != 1) {
                throw new OptimisticLockingFailureException("记录已变化，请重新读取后保存");
            }
        }
        deletePermissionLinks(entity.getRoleCode());
        if (!entity.getPermissions().isEmpty()) {
            insertPermissionLinks(entity.getRoleCode(), entity.getPermissions().stream().map(SysPermission::getPermissionCode).toList());
        }
        return entity;
    }

    int insert(SysRole entity);
    int update(SysRole entity);
    Optional<SysRole> findById(@Param("id") String id);
    boolean existsById(@Param("id") String id);
    /** 先分页角色，再关联本页权限，避免 JOIN 放大页数或逐角色查询。 */
    List<SysRole> findListPage(@Param("query") String query,
            @Param("pageable") org.springframework.data.domain.Pageable pageable);
    com.ai.security.dto.RoleListCounts countList(@Param("query") String query);
    List<SysRole> findAllById(@Param("ids") Collection<String> ids);
    void deleteById(@Param("id") String id);

    default void delete(SysRole entity) {
        deleteById(entity.getRoleCode());
    }

    @Transactional
    default List<SysRole> saveAll(Iterable<SysRole> entities) {
        List<SysRole> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    List<SysRole> findByEnabledTrueOrderByRoleCodeAsc();

    List<SysRole> findByRoleCodeIn(@Param("roleCodes") Collection<String> roleCodes);

    void deletePermissionLinks(@Param("roleCode") String roleCode);
    void insertPermissionLinks(@Param("roleCode") String roleCode, @Param("permissionCodes") List<String> permissionCodes);
    List<SysRole> findRolesByUserId(@Param("userId") String userId);
}
