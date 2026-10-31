package com.ai.agent.durable;

import com.ai.agent.runtime.AgentRunStatus;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** AgentRunStateEntity 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface AgentRunStateRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default AgentRunStateEntity save(AgentRunStateEntity entity) {
        boolean creating = entity.getRunId() == null || !existsById(entity.getRunId());
        if (creating) {
            entity.onCreate();
            insert(entity);
        } else {
            entity.onUpdate();
            if (update(entity) != 1) {
                throw new OptimisticLockingFailureException("记录已变化，请重新读取后保存");
            }
            entity.setVersion(entity.getVersion() + 1);
        }
        return entity;
    }

    int insert(AgentRunStateEntity entity);
    int update(AgentRunStateEntity entity);
    Optional<AgentRunStateEntity> findById(@Param("id") String id);
    Optional<AgentRunStateEntity> findForUpdate(@Param("id") String id);
    boolean existsById(@Param("id") String id);
    List<AgentRunStateEntity> findAll();
    List<AgentRunStateEntity> findAllById(@Param("ids") Collection<String> ids);
    void deleteById(@Param("id") String id);

    default void delete(AgentRunStateEntity entity) {
        deleteById(entity.getRunId());
    }

    @Transactional
    default List<AgentRunStateEntity> saveAll(Iterable<AgentRunStateEntity> entities) {
        List<AgentRunStateEntity> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    int bindMode(@Param("runId") String runId, @Param("mode") com.ai.agent.runtime.AgentExecutionMode mode);

    Optional<AgentRunStateEntity> findByRunIdAndTenantIdAndUserId(@Param("runId") String runId,
            @Param("tenantId") String tenantId,
            @Param("userId") String userId);

    List<AgentRunStateEntity> findByStatusAndLeaseUntilBeforeOrderByUpdatedAtAsc(@Param("status") AgentRunStatus status,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("pageable") Pageable pageable);

    int transition(@Param("runId") String runId,
            @Param("expectedStatus") AgentRunStatus expectedStatus,
            @Param("targetStatus") AgentRunStatus targetStatus,
            @Param("reason") com.ai.agent.runtime.AgentRunTerminationReason reason,
            @Param("detail") String detail,
            @Param("completedAt") Instant completedAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion);

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

    int claimLease(@Param("runId") String runId,
            @Param("claimableStatuses") List<AgentRunStatus> claimableStatuses,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now,
            @Param("expectedVersion") long expectedVersion);

    int renewLease(@Param("runId") String runId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now,
            @Param("expectedVersion") long expectedVersion);
}
