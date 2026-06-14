package com.ai.repository;

import com.ai.model.TokenUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 令牌使用账户仓储。
 *
 * @author data-agent
 */
public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {

    /**
     * 查询租户的使用记录。
     *
     * @param tenantId 租户 ID
     * @return 使用记录列表
     */
    List<TokenUsage> findByTenantId(String tenantId);

    /**
     * 查询用户的使用记录。
     *
     * @param userId 用户 ID
     * @return 使用记录列表
     */
    List<TokenUsage> findByUserId(String userId);

    /**
     * 查询租户时间范围内的使用记录。
     *
     * @param tenantId 租户 ID
     * @param start 开始时间
     * @param end 结束时间
     * @return 使用记录列表
     */
    List<TokenUsage> findByTenantIdAndCreatedAtBetween(String tenantId, LocalDateTime start, LocalDateTime end);

    /**
     * 查询租户分页的使用记录。
     *
     * @param tenantId 租户 ID
     * @param pageable 分页请求
     * @return 使用记录分页结果
     */
    Page<TokenUsage> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * 统计租户的令牌总数。
     *
     * @param tenantId 租户 ID
     * @return 令牌总数
     */
    @Query("SELECT SUM(t.totalTokens) FROM TokenUsage t WHERE t.tenantId = :tenantId")
    Long sumTotalTokensByTenantId(@Param("tenantId") String tenantId);

    /**
     * 统计用户的令牌总数。
     *
     * @param userId 用户 ID
     * @return 令牌总数
     */
    @Query("SELECT SUM(t.totalTokens) FROM TokenUsage t WHERE t.userId = :userId")
    Long sumTotalTokensByUserId(@Param("userId") String userId);

    /**
     * 统计租户按模型分组的令牌数。
     *
     * @param tenantId 租户 ID
     * @return 模型令牌行
     */
    @Query("SELECT t.modelName, SUM(t.totalTokens) FROM TokenUsage t WHERE t.tenantId = :tenantId GROUP BY t.modelName")
    List<Object[]> sumTokensByModelGroupedByTenant(@Param("tenantId") String tenantId);

    /**
     * 统计租户按技能分组的令牌数。
     *
     * @param tenantId 租户 ID
     * @return 技能令牌行
     */
    @Query("SELECT t.skillName, SUM(t.totalTokens) FROM TokenUsage t WHERE t.tenantId = :tenantId GROUP BY t.skillName")
    List<Object[]> sumTokensBySkillGroupedByTenant(@Param("tenantId") String tenantId);

    /**
     * 统计自指定时间以来的用户令牌数。
     *
     * @param userId 用户 ID
     * @param startOfDay 开始时间
     * @return 令牌总数
     */
    @Query("SELECT SUM(t.totalTokens) FROM TokenUsage t WHERE t.userId = :userId AND t.createdAt >= :startOfDay")
    Long sumTotalTokensByUserIdSince(@Param("userId") String userId, @Param("startOfDay") LocalDateTime startOfDay);
}
