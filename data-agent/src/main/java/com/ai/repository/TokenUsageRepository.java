package com.ai.repository;

import com.ai.model.TokenUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {

    List<TokenUsage> findByTenantId(String tenantId);

    List<TokenUsage> findByUserId(String userId);

    List<TokenUsage> findByTenantIdAndCreatedAtBetween(String tenantId, LocalDateTime start, LocalDateTime end);

    Page<TokenUsage> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    @Query("SELECT SUM(t.totalTokens) FROM TokenUsage t WHERE t.tenantId = :tenantId")
    Long sumTotalTokensByTenantId(@Param("tenantId") String tenantId);

    @Query("SELECT SUM(t.totalTokens) FROM TokenUsage t WHERE t.userId = :userId")
    Long sumTotalTokensByUserId(@Param("userId") String userId);

    @Query("SELECT t.modelName, SUM(t.totalTokens) FROM TokenUsage t WHERE t.tenantId = :tenantId GROUP BY t.modelName")
    List<Object[]> sumTokensByModelGroupedByTenant(@Param("tenantId") String tenantId);

    @Query("SELECT t.skillName, SUM(t.totalTokens) FROM TokenUsage t WHERE t.tenantId = :tenantId GROUP BY t.skillName")
    List<Object[]> sumTokensBySkillGroupedByTenant(@Param("tenantId") String tenantId);
}
