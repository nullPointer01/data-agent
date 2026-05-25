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
 * Repository for token usage accounting.
 *
 * @author data-agent
 */
public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {

    /**
     * Finds usage records by tenant.
     *
     * @param tenantId tenant id
     * @return usage records
     */
    List<TokenUsage> findByTenantId(String tenantId);

    /**
     * Finds usage records by user.
     *
     * @param userId user id
     * @return usage records
     */
    List<TokenUsage> findByUserId(String userId);

    /**
     * Finds tenant usage records within a time range.
     *
     * @param tenantId tenant id
     * @param start start time
     * @param end end time
     * @return usage records
     */
    List<TokenUsage> findByTenantIdAndCreatedAtBetween(String tenantId, LocalDateTime start, LocalDateTime end);

    /**
     * Finds paged tenant usage records.
     *
     * @param tenantId tenant id
     * @param pageable page request
     * @return usage page
     */
    Page<TokenUsage> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * Sums total tokens by tenant.
     *
     * @param tenantId tenant id
     * @return token total
     */
    @Query("SELECT SUM(t.totalTokens) FROM TokenUsage t WHERE t.tenantId = :tenantId")
    Long sumTotalTokensByTenantId(@Param("tenantId") String tenantId);

    /**
     * Sums total tokens by user.
     *
     * @param userId user id
     * @return token total
     */
    @Query("SELECT SUM(t.totalTokens) FROM TokenUsage t WHERE t.userId = :userId")
    Long sumTotalTokensByUserId(@Param("userId") String userId);

    /**
     * Sums tenant tokens grouped by model.
     *
     * @param tenantId tenant id
     * @return model token rows
     */
    @Query("SELECT t.modelName, SUM(t.totalTokens) FROM TokenUsage t WHERE t.tenantId = :tenantId GROUP BY t.modelName")
    List<Object[]> sumTokensByModelGroupedByTenant(@Param("tenantId") String tenantId);

    /**
     * Sums tenant tokens grouped by skill.
     *
     * @param tenantId tenant id
     * @return skill token rows
     */
    @Query("SELECT t.skillName, SUM(t.totalTokens) FROM TokenUsage t WHERE t.tenantId = :tenantId GROUP BY t.skillName")
    List<Object[]> sumTokensBySkillGroupedByTenant(@Param("tenantId") String tenantId);

    /**
     * Sums user tokens since the given time.
     *
     * @param userId user id
     * @param startOfDay start time
     * @return token total
     */
    @Query("SELECT SUM(t.totalTokens) FROM TokenUsage t WHERE t.userId = :userId AND t.createdAt >= :startOfDay")
    Long sumTotalTokensByUserIdSince(@Param("userId") String userId, @Param("startOfDay") LocalDateTime startOfDay);
}
