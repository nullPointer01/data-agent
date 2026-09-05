package com.ai.repository;

import com.ai.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 租户审计日志仓储。
 *
 * @author data-agent
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * 查询租户审计日志。
     *
     * @param tenantId 租户编号
     * @param pageable 分页参数
     * @return 审计日志列表
     */
    List<AuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * 查询租户下某个用户的审计日志。
     *
     * @param tenantId 租户编号
     * @param userId 用户编号
     * @param pageable 分页参数
     * @return 审计日志列表
     */
    List<AuditLog> findByTenantIdAndUserIdOrderByCreatedAtDesc(String tenantId, String userId, Pageable pageable);
}
