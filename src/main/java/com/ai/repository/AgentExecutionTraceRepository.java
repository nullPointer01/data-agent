package com.ai.repository;

import com.ai.model.AgentExecutionTrace;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 执行轨迹仓储。
 *
 * @author data-agent
 */
public interface AgentExecutionTraceRepository extends JpaRepository<AgentExecutionTrace, Long> {

    /**
     * 查询当前租户的执行轨迹。
     *
     * @param tenantId 租户编号
     * @param pageable 分页参数
     * @return 执行轨迹列表
     */
    List<AgentExecutionTrace> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * 查询当前租户下指定用户的执行轨迹。
     *
     * @param tenantId 租户编号
     * @param userId 用户编号
     * @param pageable 分页参数
     * @return 执行轨迹列表
     */
    List<AgentExecutionTrace> findByTenantIdAndUserIdOrderByCreatedAtDesc(String tenantId, String userId,
            Pageable pageable);

    /**
     * 查询当前租户下的单条执行轨迹。
     *
     * @param traceId 轨迹编号
     * @param tenantId 租户编号
     * @return 执行轨迹
     */
    Optional<AgentExecutionTrace> findByTraceIdAndTenantId(String traceId, String tenantId);
}
