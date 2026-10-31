package com.ai.security.rbac;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** SysPermission 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface SysPermissionRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default SysPermission save(SysPermission entity) {
        boolean creating = entity.getPermissionCode() == null || !existsById(entity.getPermissionCode());
        if (creating) {
            entity.onCreate();
            insert(entity);
        } else {
            entity.onUpdate();
            if (update(entity) != 1) {
                throw new OptimisticLockingFailureException("记录已变化，请重新读取后保存");
            }
        }
        return entity;
    }

    int insert(SysPermission entity);
    int update(SysPermission entity);
    Optional<SysPermission> findById(@Param("id") String id);
    boolean existsById(@Param("id") String id);
    List<SysPermission> findListPage(@Param("query") String query,
            @Param("pageable") org.springframework.data.domain.Pageable pageable);
    long countList(@Param("query") String query);
    List<SysPermission> findAllById(@Param("ids") Collection<String> ids);
    void deleteById(@Param("id") String id);

    default void delete(SysPermission entity) {
        deleteById(entity.getPermissionCode());
    }

    @Transactional
    default List<SysPermission> saveAll(Iterable<SysPermission> entities) {
        List<SysPermission> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    List<SysPermission> findByEnabledTrueOrderByPermissionCodeAsc();

    List<SysPermission> findByPermissionCodeIn(@Param("permissionCodes") Collection<String> permissionCodes);

    List<SysPermission> findPermissionsByRoleCode(@Param("roleCode") String roleCode);
}
