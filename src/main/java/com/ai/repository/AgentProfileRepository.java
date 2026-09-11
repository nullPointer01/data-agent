package com.ai.repository;

import com.ai.model.AgentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 租户隔离的 Agent 配置仓储。
 *
 * @author data-agent
 */
public interface AgentProfileRepository extends JpaRepository<AgentProfile, String> {

    /**
     * 查询租户下全部 Agent 配置，按更新时间倒序排列。
     *
     * @param tenantId 租户编号
     * @return Agent 配置列表
     */
    List<AgentProfile> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    /**
     * 查询租户下已启用 Agent 配置，按更新时间倒序排列。
     *
     * @param tenantId 租户编号
     * @return 已启用 Agent 配置列表
     */
    List<AgentProfile> findByTenantIdAndEnabledTrueOrderByUpdatedAtDesc(String tenantId);

    /**
     * 查询当前用户创建的 Agent 配置，按更新时间倒序排列。
     *
     * @param tenantId 租户编号
     * @param createdBy 创建人编号
     * @return Agent 配置列表
     */
    List<AgentProfile> findByTenantIdAndCreatedByOrderByUpdatedAtDesc(String tenantId, String createdBy);

    /**
     * 查询当前用户创建且已启用的 Agent 配置，按更新时间倒序排列。
     *
     * @param tenantId 租户编号
     * @param createdBy 创建人编号
     * @return Agent 配置列表
     */
    List<AgentProfile> findByTenantIdAndCreatedByAndEnabledTrueOrderByUpdatedAtDesc(String tenantId, String createdBy);

    /**
     * 按租户和 Agent 编号查询单个配置。
     *
     * @param agentId Agent 编号
     * @param tenantId 租户编号
     * @return 匹配的 Agent 配置
     */
    Optional<AgentProfile> findByAgentIdAndTenantId(String agentId, String tenantId);

    /**
     * 按租户、创建人和 Agent 编号查询单个配置。
     *
     * @param agentId Agent 编号
     * @param tenantId 租户编号
     * @param createdBy 创建人编号
     * @return 匹配的 Agent 配置
     */
    Optional<AgentProfile> findByAgentIdAndTenantIdAndCreatedBy(String agentId, String tenantId, String createdBy);

    /**
     * 查询当前用户的默认个人 Agent。
     */
    Optional<AgentProfile> findFirstByTenantIdAndCreatedByAndDefaultAgentTrueOrderByUpdatedAtDesc(
            String tenantId, String createdBy);
}
