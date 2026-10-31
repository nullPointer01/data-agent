package com.ai.repository;

import com.ai.memory.UserProfileMemory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** UserProfileMemory 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface UserProfileMemoryRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default UserProfileMemory save(UserProfileMemory entity) {
        boolean creating = entity.getProfileId() == null || !existsById(entity.getProfileId());
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

    int insert(UserProfileMemory entity);
    int update(UserProfileMemory entity);
    Optional<UserProfileMemory> findById(@Param("id") String id);
    boolean existsById(@Param("id") String id);
    List<UserProfileMemory> findAll();
    List<UserProfileMemory> findAllById(@Param("ids") Collection<String> ids);
    void deleteById(@Param("id") String id);

    default void delete(UserProfileMemory entity) {
        deleteById(entity.getProfileId());
    }

    @Transactional
    default List<UserProfileMemory> saveAll(Iterable<UserProfileMemory> entities) {
        List<UserProfileMemory> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    Optional<UserProfileMemory> findByTenantIdAndUserId(@Param("tenantId") String tenantId, @Param("userId") String userId);
}
