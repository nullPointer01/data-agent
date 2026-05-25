package com.ai.repository;

import com.ai.model.AgentFeedback;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 反馈仓储。
 *
 * @author data-agent
 */
public interface AgentFeedbackRepository extends JpaRepository<AgentFeedback, Long> {

    /**
     * 查询租户反馈列表。
     *
     * @param tenantId 租户编号
     * @param pageable 分页参数
     * @return 反馈列表
     */
    List<AgentFeedback> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * 查询租户最近的指定评分反馈。
     *
     * @param tenantId 租户编号
     * @param rating 评分
     * @param pageable 分页参数
     * @return 反馈列表
     */
    List<AgentFeedback> findByTenantIdAndRatingOrderByCreatedAtDesc(String tenantId, String rating, Pageable pageable);

    /**
     * 查询租户下某条执行轨迹关联的反馈。
     *
     * @param tenantId 租户编号
     * @param traceId 轨迹编号
     * @param pageable 分页参数
     * @return 反馈列表
     */
    List<AgentFeedback> findByTenantIdAndTraceIdOrderByCreatedAtDesc(String tenantId, String traceId,
            Pageable pageable);

    /**
     * 查询租户下某条执行轨迹关联的指定评分反馈。
     *
     * @param tenantId 租户编号
     * @param traceId 轨迹编号
     * @param rating 评分
     * @param pageable 分页参数
     * @return 反馈列表
     */
    List<AgentFeedback> findByTenantIdAndTraceIdAndRatingOrderByCreatedAtDesc(String tenantId, String traceId,
            String rating, Pageable pageable);

    /**
     * 统计租户反馈总数。
     *
     * @param tenantId 租户编号
     * @return 反馈总数
     */
    long countByTenantId(String tenantId);

    /**
     * 按评分统计租户反馈数。
     *
     * @param tenantId 租户编号
     * @param rating 评分
     * @return 反馈数
     */
    long countByTenantIdAndRating(String tenantId, String rating);

    /**
     * 查询租户最近一条指定评分反馈。
     *
     * @param tenantId 租户编号
     * @param rating 评分
     * @return 反馈
     */
    Optional<AgentFeedback> findFirstByTenantIdAndRatingOrderByCreatedAtDesc(String tenantId, String rating);

    /**
     * 查询当前用户对某条轨迹的反馈。
     *
     * @param traceId 轨迹编号
     * @param userId 用户编号
     * @param tenantId 租户编号
     * @return 反馈
     */
    Optional<AgentFeedback> findByTraceIdAndUserIdAndTenantId(String traceId, String userId, String tenantId);
}
