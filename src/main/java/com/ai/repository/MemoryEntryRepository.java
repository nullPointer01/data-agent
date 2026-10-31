package com.ai.repository;

import com.ai.memory.MemoryEntry;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;
import com.ai.api.RecordCursor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** MemoryEntry 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface MemoryEntryRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default MemoryEntry save(MemoryEntry entity) {
        boolean creating = entity.getMemoryId() == null || !existsById(entity.getMemoryId());
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

    int insert(MemoryEntry entity);
    int update(MemoryEntry entity);

    /** 维护只更新仍存在且未变化的长期记忆，禁止陈旧实体走 save 后重新插入。 */
    int updateLongTermDecayIfUnchanged(MemoryEntry entity);

    Optional<MemoryEntry> findById(@Param("id") String id);
    boolean existsById(@Param("id") String id);
    List<MemoryEntry> findAll();
    /** 个人记忆列表使用不可变创建时间和 ID 游标，不受画像更新或后台衰减挤页影响。 */
    List<MemoryEntry> findCursorPage(@Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("tier") MemoryTier tier, @Param("cursor") RecordCursor cursor, @Param("limit") int limit);
    List<MemoryEntry> findAllById(@Param("ids") Collection<String> ids);

    /** 异步向量召回后批量核验主库；身份、层级与有效期在 SQL 中约束。 */
    List<MemoryEntry> findActiveLongTermByIds(@Param("ids") Collection<String> ids,
            @Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("now") LocalDateTime now);

    void deleteById(@Param("id") String id);

    default void delete(MemoryEntry entity) {
        deleteById(entity.getMemoryId());
    }

    @Transactional
    default void deleteAll(Iterable<MemoryEntry> entities) {
        entities.forEach(this::delete);
    }

    @Transactional
    default List<MemoryEntry> saveAll(Iterable<MemoryEntry> entities) {
        List<MemoryEntry> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    List<MemoryEntry> findActiveByTenantUserAndSession(@Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("now") LocalDateTime now,
            @Param("pageable") Pageable pageable);

    List<MemoryEntry> findByTenantIdAndUserIdOrderByCreatedAtDesc(@Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("pageable") Pageable pageable);

    List<MemoryEntry> findByTenantIdAndUserIdAndTierOrderByCreatedAtDesc(@Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("tier") MemoryTier tier,
            @Param("pageable") Pageable pageable);

    List<MemoryEntry> findByTenantIdAndUserIdAndTierAndTypeOrderByUpdatedAtDesc(@Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("tier") MemoryTier tier,
            @Param("type") MemoryType type,
            @Param("pageable") Pageable pageable);

    List<MemoryEntry> findByTenantIdAndUserIdAndTierAndTypeInOrderByUpdatedAtDesc(@Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("tier") MemoryTier tier,
            @Param("types") List<MemoryType> types,
            @Param("pageable") Pageable pageable);

    /** 分批读取本人结构化画像的依据；范围与画像投影共用稳定键，保留其他长期记忆。 */
    List<MemoryEntry> findProfileEvidenceForReset(@Param("tenantId") String tenantId,
            @Param("userId") String userId, @Param("keys") List<String> keys,
            @Param("prefixes") List<String> prefixes, @Param("limit") int limit);

    Optional<MemoryEntry> findByTenantIdAndUserIdAndTypeAndSemanticKey(@Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("type") MemoryType type,
            @Param("semanticKey") String semanticKey);

    List<MemoryEntry> findByTierOrderByUpdatedAtAsc(@Param("tier") MemoryTier tier, @Param("pageable") Pageable pageable);

    List<MemoryEntry> findPrunableMemories(@Param("tenantId") String tenantId, @Param("userId") String userId, @Param("pageable") Pageable pageable);

    List<MemoryEntry> findPromotionCandidates(@Param("minDecayWeight") double minDecayWeight,
            @Param("minAccessCount") int minAccessCount,
            @Param("now") LocalDateTime now,
            @Param("pageable") Pageable pageable);

    Optional<MemoryEntry> findByMemoryIdAndTenantIdAndUserId(@Param("memoryId") String memoryId,
            @Param("tenantId") String tenantId,
            @Param("userId") String userId);

    long countByTenantIdAndUserId(@Param("tenantId") String tenantId, @Param("userId") String userId);

    long countByTenantIdAndUserIdAndTier(@Param("tenantId") String tenantId, @Param("userId") String userId, @Param("tier") MemoryTier tier);

    long countByTenantIdAndUserIdAndType(@Param("tenantId") String tenantId, @Param("userId") String userId, @Param("type") MemoryType type);

    long sumSourceContentLengthByTenantUser(@Param("tenantId") String tenantId, @Param("userId") String userId);

    long sumStoredContentLengthByTenantUser(@Param("tenantId") String tenantId, @Param("userId") String userId);

    List<MemoryEntry> findExpiredMemories(@Param("now") LocalDateTime now, @Param("pageable") Pageable pageable);
}
