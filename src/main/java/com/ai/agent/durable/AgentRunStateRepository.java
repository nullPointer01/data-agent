package com.ai.agent.durable;

import com.ai.agent.runtime.AgentRunStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 持久化 Agent Run 仓储。状态写入由 AgentDurableRunStore 统一封装。
 *
 * @author data-agent
 */
public interface AgentRunStateRepository extends JpaRepository<AgentRunStateEntity, String> {

    Optional<AgentRunStateEntity> findByRunIdAndTenantIdAndUserId(String runId, String tenantId, String userId);

    List<AgentRunStateEntity> findByStatusAndLeaseUntilBeforeOrderByUpdatedAtAsc(
            AgentRunStatus status, Instant leaseUntil, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentRunStateEntity r
               set r.status = :targetStatus,
                   r.terminationReason = :reason,
                   r.errorSummary = :detail,
                   r.completedAt = :completedAt,
                   r.updatedAt = :updatedAt,
                   r.leaseOwner = null,
                   r.leaseUntil = null,
                   r.version = r.version + 1
             where r.runId = :runId
               and r.status = :expectedStatus
               and r.version = :expectedVersion
            """)
    int transition(@Param("runId") String runId,
            @Param("expectedStatus") AgentRunStatus expectedStatus,
            @Param("targetStatus") AgentRunStatus targetStatus,
            @Param("reason") com.ai.agent.runtime.AgentRunTerminationReason reason,
            @Param("detail") String detail,
            @Param("completedAt") Instant completedAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentRunStateEntity r
               set r.status = :targetStatus,
                   r.terminationReason = :reason,
                   r.errorSummary = :detail,
                   r.resultSummary = :resultSummary,
                   r.usedIterations = :usedIterations,
                   r.usedModelCalls = :usedModelCalls,
                   r.usedToolCalls = :usedToolCalls,
                   r.usedTokens = :usedTokens,
                   r.tokenUsageEstimated = :tokenUsageEstimated,
                   r.remainingActiveTimeoutMs = :remainingActiveTimeoutMs,
                   r.completedAt = :completedAt,
                   r.updatedAt = :updatedAt,
                   r.leaseOwner = null,
                   r.leaseUntil = null,
                   r.version = r.version + 1
             where r.runId = :runId
               and r.status = :expectedStatus
               and r.version = :expectedVersion
            """)
    int terminate(@Param("runId") String runId,
            @Param("expectedStatus") AgentRunStatus expectedStatus,
            @Param("targetStatus") AgentRunStatus targetStatus,
            @Param("reason") com.ai.agent.runtime.AgentRunTerminationReason reason,
            @Param("detail") String detail,
            @Param("resultSummary") String resultSummary,
            @Param("usedIterations") int usedIterations,
            @Param("usedModelCalls") int usedModelCalls,
            @Param("usedToolCalls") int usedToolCalls,
            @Param("usedTokens") long usedTokens,
            @Param("tokenUsageEstimated") boolean tokenUsageEstimated,
            @Param("remainingActiveTimeoutMs") long remainingActiveTimeoutMs,
            @Param("completedAt") Instant completedAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentRunStateEntity r
               set r.status = com.ai.agent.runtime.AgentRunStatus.WAITING_APPROVAL,
                   r.terminationReason = com.ai.agent.runtime.AgentRunTerminationReason.NONE,
                   r.checkpointCiphertext = :checkpointCiphertext,
                   r.checkpointSchemaVersion = :checkpointSchemaVersion,
                   r.usedIterations = :usedIterations,
                   r.usedModelCalls = :usedModelCalls,
                   r.usedToolCalls = :usedToolCalls,
                   r.usedTokens = :usedTokens,
                   r.tokenUsageEstimated = :tokenUsageEstimated,
                   r.remainingActiveTimeoutMs = :remainingActiveTimeoutMs,
                   r.approvalId = :approvalId,
                   r.approvalExpiresAt = :approvalExpiresAt,
                   r.updatedAt = :updatedAt,
                   r.leaseOwner = null,
                   r.leaseUntil = null,
                   r.version = r.version + 1
             where r.runId = :runId
               and r.status = :expectedStatus
               and r.version = :expectedVersion
            """)
    int suspendForApproval(@Param("runId") String runId,
            @Param("expectedStatus") AgentRunStatus expectedStatus,
            @Param("checkpointCiphertext") String checkpointCiphertext,
            @Param("checkpointSchemaVersion") int checkpointSchemaVersion,
            @Param("usedIterations") int usedIterations,
            @Param("usedModelCalls") int usedModelCalls,
            @Param("usedToolCalls") int usedToolCalls,
            @Param("usedTokens") long usedTokens,
            @Param("tokenUsageEstimated") boolean tokenUsageEstimated,
            @Param("remainingActiveTimeoutMs") long remainingActiveTimeoutMs,
            @Param("approvalId") String approvalId,
            @Param("approvalExpiresAt") Instant approvalExpiresAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentRunStateEntity r
               set r.status = com.ai.agent.runtime.AgentRunStatus.RESUMING,
                   r.leaseOwner = :leaseOwner,
                   r.leaseUntil = :leaseUntil,
                   r.resumeAttempts = r.resumeAttempts + 1,
                   r.updatedAt = :now,
                   r.version = r.version + 1
             where r.runId = :runId
               and r.status in :claimableStatuses
               and (r.leaseUntil is null or r.leaseUntil < :now)
               and r.version = :expectedVersion
            """)
    int claimLease(@Param("runId") String runId,
            @Param("claimableStatuses") List<AgentRunStatus> claimableStatuses,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now,
            @Param("expectedVersion") long expectedVersion);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentRunStateEntity r
               set r.leaseUntil = :leaseUntil,
                   r.updatedAt = :now,
                   r.version = r.version + 1
             where r.runId = :runId
               and r.status = com.ai.agent.runtime.AgentRunStatus.RESUMING
               and r.leaseOwner = :leaseOwner
               and r.version = :expectedVersion
            """)
    int renewLease(@Param("runId") String runId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now,
            @Param("expectedVersion") long expectedVersion);
}
