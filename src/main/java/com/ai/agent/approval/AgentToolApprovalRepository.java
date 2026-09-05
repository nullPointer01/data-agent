package com.ai.agent.approval;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 工具审批仓储。决定更新必须通过审批服务的条件更新入口。
 *
 * @author data-agent
 */
public interface AgentToolApprovalRepository extends JpaRepository<AgentToolApprovalEntity, String> {

    Optional<AgentToolApprovalEntity> findByApprovalIdAndTenantId(String approvalId, String tenantId);

    Optional<AgentToolApprovalEntity> findByTenantIdAndRunIdAndToolCallId(
            String tenantId, String runId, String toolCallId);

    List<AgentToolApprovalEntity> findByTenantIdAndDecisionStatusOrderByRequestedAtDesc(
            String tenantId, AgentApprovalDecisionStatus decisionStatus, Pageable pageable);

    List<AgentToolApprovalEntity> findByTenantIdOrderByRequestedAtDesc(String tenantId, Pageable pageable);

    List<AgentToolApprovalEntity> findByDecisionStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            AgentApprovalDecisionStatus decisionStatus, Instant expiresAt, Pageable pageable);

    List<AgentToolApprovalEntity> findByDecisionStatusAndExecutionStatusOrderByDecidedAtAsc(
            AgentApprovalDecisionStatus decisionStatus,
            AgentApprovalExecutionStatus executionStatus,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentToolApprovalEntity a
               set a.decisionStatus = com.ai.agent.approval.AgentApprovalDecisionStatus.CANCELLED,
                   a.executionStatus = com.ai.agent.approval.AgentApprovalExecutionStatus.CANCELLED,
                   a.decidedAt = :now,
                   a.updatedAt = :now,
                   a.version = a.version + 1
             where a.tenantId = :tenantId
               and a.runId = :runId
               and a.decisionStatus = com.ai.agent.approval.AgentApprovalDecisionStatus.PENDING
            """)
    int cancelPendingByRun(@Param("tenantId") String tenantId,
            @Param("runId") String runId,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentToolApprovalEntity a
               set a.decisionStatus = :decisionStatus,
                   a.executionStatus = :executionStatus,
                   a.reviewerUserId = :reviewerUserId,
                   a.decisionComment = :decisionComment,
                   a.decidedAt = :now,
                   a.updatedAt = :now,
                   a.version = a.version + 1
             where a.approvalId = :approvalId
               and a.tenantId = :tenantId
               and a.decisionStatus = com.ai.agent.approval.AgentApprovalDecisionStatus.PENDING
               and a.expiresAt > :now
               and a.version = :expectedVersion
            """)
    int decide(@Param("approvalId") String approvalId,
            @Param("tenantId") String tenantId,
            @Param("decisionStatus") AgentApprovalDecisionStatus decisionStatus,
            @Param("executionStatus") AgentApprovalExecutionStatus executionStatus,
            @Param("reviewerUserId") String reviewerUserId,
            @Param("decisionComment") String decisionComment,
            @Param("now") Instant now,
            @Param("expectedVersion") long expectedVersion);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentToolApprovalEntity a
               set a.decisionStatus = com.ai.agent.approval.AgentApprovalDecisionStatus.EXPIRED,
                   a.executionStatus = com.ai.agent.approval.AgentApprovalExecutionStatus.CANCELLED,
                   a.decidedAt = :now,
                   a.updatedAt = :now,
                   a.version = a.version + 1
             where a.approvalId = :approvalId
               and a.decisionStatus = com.ai.agent.approval.AgentApprovalDecisionStatus.PENDING
               and a.expiresAt <= :now
               and a.version = :expectedVersion
            """)
    int expire(@Param("approvalId") String approvalId,
            @Param("now") Instant now,
            @Param("expectedVersion") long expectedVersion);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentToolApprovalEntity a
               set a.executionStatus = com.ai.agent.approval.AgentApprovalExecutionStatus.RUNNING,
                   a.executionStartedAt = :now,
                   a.updatedAt = :now,
                   a.version = a.version + 1
             where a.approvalId = :approvalId
               and a.decisionStatus = com.ai.agent.approval.AgentApprovalDecisionStatus.APPROVED
               and a.executionStatus in :claimableStatuses
               and a.version = :expectedVersion
            """)
    int startExecution(@Param("approvalId") String approvalId,
            @Param("claimableStatuses") List<AgentApprovalExecutionStatus> claimableStatuses,
            @Param("now") Instant now,
            @Param("expectedVersion") long expectedVersion);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentToolApprovalEntity a
               set a.executionStatus = :targetStatus,
                   a.executionCompletedAt = :now,
                   a.updatedAt = :now,
                   a.version = a.version + 1
             where a.approvalId = :approvalId
               and a.decisionStatus = com.ai.agent.approval.AgentApprovalDecisionStatus.APPROVED
               and a.executionStatus = com.ai.agent.approval.AgentApprovalExecutionStatus.RUNNING
               and a.version = :expectedVersion
            """)
    int completeExecution(@Param("approvalId") String approvalId,
            @Param("targetStatus") AgentApprovalExecutionStatus targetStatus,
            @Param("now") Instant now,
            @Param("expectedVersion") long expectedVersion);
}
