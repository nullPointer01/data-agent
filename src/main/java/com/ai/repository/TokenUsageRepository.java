package com.ai.repository;

import com.ai.model.TokenUsage;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** TokenUsage 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface TokenUsageRepository {

    /** 与日余额、预占终态在同一事务保存，唯一实际调用ID支持 UNKNOWN 后续校正。 */
    int upsertAccounting(TokenUsage entity);

    List<TokenUsage> selectUsagePage(@Param("tenantId") String tenantId, @Param("pageable") Pageable pageable);
    long countByTenantId(@Param("tenantId") String tenantId);

    Long sumTotalTokensByTenantId(@Param("tenantId") String tenantId);

    Long sumTotalTokensByOwner(@Param("tenantId") String tenantId, @Param("userId") String userId);

    List<java.util.Map<String, Object>> sumTokensByModelGroupedByTenantRows(@Param("tenantId") String tenantId);
    default List<Object[]> sumTokensByModelGroupedByTenant(@Param("tenantId") String tenantId) {
        return sumTokensByModelGroupedByTenantRows(tenantId).stream()
                .map(row -> new Object[]{row.get("label"), row.get("total_value")}).toList();
    }

    List<java.util.Map<String, Object>> sumTokensBySkillGroupedByTenantRows(@Param("tenantId") String tenantId);
    default List<Object[]> sumTokensBySkillGroupedByTenant(@Param("tenantId") String tenantId) {
        return sumTokensBySkillGroupedByTenantRows(tenantId).stream()
                .map(row -> new Object[]{row.get("label"), row.get("total_value")}).toList();
    }

}
