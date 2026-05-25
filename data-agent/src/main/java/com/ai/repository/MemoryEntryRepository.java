package com.ai.repository;

import com.ai.memory.MemoryEntry;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 持久化记忆条目仓储。
 *
 * @author data-agent
 */
public interface MemoryEntryRepository extends JpaRepository<MemoryEntry, String> {

    /**
     * 查询指定租户用户和层级下仍有效的记忆。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param tier 记忆层级
     * @param now 当前时间
     * @param pageable 分页参数
     * @return 按权重排序的有效记忆
     */
    @Query("""
            SELECT m FROM MemoryEntry m
            WHERE m.tenantId = :tenantId
              AND m.userId = :userId
              AND m.tier = :tier
              AND (m.expiresAt IS NULL OR m.expiresAt > :now)
            ORDER BY m.decayWeight DESC, m.lastAccessedAt DESC
            """)
    List<MemoryEntry> findActiveByTenantUserAndTier(@Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("tier") MemoryTier tier,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    /**
     * 查询指定会话下仍有效的短期记忆。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param now 当前时间
     * @param pageable 分页参数
     * @return 会话记忆
     */
    @Query("""
            SELECT m FROM MemoryEntry m
            WHERE m.tenantId = :tenantId
              AND m.userId = :userId
              AND m.sessionId = :sessionId
              AND (m.expiresAt IS NULL OR m.expiresAt > :now)
            ORDER BY m.createdAt DESC
            """)
    List<MemoryEntry> findActiveByTenantUserAndSession(@Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    /**
     * 查询指定租户用户的全部记忆。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param pageable 分页参数
     * @return 记忆条目
     */
    List<MemoryEntry> findByTenantIdAndUserIdOrderByCreatedAtDesc(String tenantId, String userId, Pageable pageable);

    /**
     * 查询指定租户用户和层级下的全部记忆。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param tier 记忆层级
     * @param pageable 分页参数
     * @return 记忆条目
     */
    List<MemoryEntry> findByTenantIdAndUserIdAndTierOrderByCreatedAtDesc(String tenantId, String userId,
            MemoryTier tier, Pageable pageable);

    /**
     * 查询指定层级下需要进行衰减维护的记忆。
     *
     * @param tier 记忆层级
     * @param pageable 分页参数
     * @return 记忆列表
     */
    List<MemoryEntry> findByTierOrderByUpdatedAtAsc(MemoryTier tier, Pageable pageable);

    /**
     * 查询超过用户配额时应优先淘汰的低价值记忆。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param pageable 分页参数
     * @return 低价值记忆列表
     */
    @Query("""
            SELECT m FROM MemoryEntry m
            WHERE m.tenantId = :tenantId
              AND m.userId = :userId
            ORDER BY m.decayWeight ASC, m.lastAccessedAt ASC, m.createdAt ASC
            """)
    List<MemoryEntry> findPrunableMemories(@Param("tenantId") String tenantId,
            @Param("userId") String userId,
            Pageable pageable);

    /**
     * 查询可以从短期晋升为长期的高价值记忆。
     *
     * @param minDecayWeight 最小衰减权重
     * @param minAccessCount 最小访问次数
     * @param now 当前时间
     * @param pageable 分页参数
     * @return 候选记忆
     */
    @Query("""
            SELECT m FROM MemoryEntry m
            WHERE m.tier = com.ai.memory.MemoryTier.SHORT_TERM
              AND m.decayWeight >= :minDecayWeight
              AND m.accessCount >= :minAccessCount
              AND (m.expiresAt IS NULL OR m.expiresAt > :now)
            ORDER BY m.decayWeight DESC, m.accessCount DESC, m.lastAccessedAt DESC
            """)
    List<MemoryEntry> findPromotionCandidates(@Param("minDecayWeight") double minDecayWeight,
            @Param("minAccessCount") int minAccessCount,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    /**
     * 查询指定租户用户拥有的一条记忆。
     *
     * @param memoryId 记忆 ID
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 记忆条目
     */
    Optional<MemoryEntry> findByMemoryIdAndTenantIdAndUserId(String memoryId, String tenantId, String userId);

    /**
     * 统计指定租户用户拥有的记忆数量。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 记忆数量
     */
    long countByTenantIdAndUserId(String tenantId, String userId);

    /**
     * 统计指定租户用户和层级下的记忆数量。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param tier 记忆层级
     * @return 记忆数量
     */
    long countByTenantIdAndUserIdAndTier(String tenantId, String userId, MemoryTier tier);

    /**
     * 统计当前租户用户指定类型的记忆数量。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param type 记忆类型
     * @return 记忆数量
     */
    long countByTenantIdAndUserIdAndType(String tenantId, String userId, MemoryType type);

    /**
     * 汇总指定租户用户记忆写入前的原始文本长度。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 原始文本字符数
     */
    @Query("""
            SELECT COALESCE(SUM(m.sourceContentLength), 0)
            FROM MemoryEntry m
            WHERE m.tenantId = :tenantId
              AND m.userId = :userId
            """)
    long sumSourceContentLengthByTenantUser(@Param("tenantId") String tenantId, @Param("userId") String userId);

    /**
     * 汇总指定租户用户记忆实际存储的文本长度。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 实际存储字符数
     */
    @Query("""
            SELECT COALESCE(SUM(m.storedContentLength), 0)
            FROM MemoryEntry m
            WHERE m.tenantId = :tenantId
              AND m.userId = :userId
            """)
    long sumStoredContentLengthByTenantUser(@Param("tenantId") String tenantId, @Param("userId") String userId);

    /**
     * 查询已经过期的记忆。
     *
     * @param now 当前时间
     * @param pageable 分页参数
     * @return 过期记忆
     */
    @Query("""
            SELECT m FROM MemoryEntry m
            WHERE m.expiresAt IS NOT NULL
              AND m.expiresAt <= :now
            ORDER BY m.expiresAt ASC
            """)
    List<MemoryEntry> findExpiredMemories(@Param("now") LocalDateTime now, Pageable pageable);
}
