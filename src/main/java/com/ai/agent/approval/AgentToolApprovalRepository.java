package com.ai.agent.approval;

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

/** AgentToolApprovalEntity 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface AgentToolApprovalRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default AgentToolApprovalEntity save(AgentToolApprovalEntity entity) {
        boolean creating = entity.getApprovalId() == null || !existsById(entity.getApprovalId());
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

    int insert(AgentToolApprovalEntity entity);
    int update(AgentToolApprovalEntity entity);
    Optional<AgentToolApprovalEntity> findById(@Param("id") String id);
    /** 个人 Run 只补执行状态，不读取审批参数或加密现场。 */
    Optional<AgentApprovalExecutionStatus> findExecutionStatus(@Param("approvalId") String approvalId,
            @Param("tenantId") String tenantId, @Param("userId") String userId);
    boolean existsById(@Param("id") String id);
    List<AgentToolApprovalEntity> findAll();
    List<AgentToolApprovalEntity> findAllById(@Param("ids") Collection<String> ids);
    void deleteById(@Param("id") String id);

    default void delete(AgentToolApprovalEntity entity) {
        deleteById(entity.getApprovalId());
    }

    @Transactional
    default List<AgentToolApprovalEntity> saveAll(Iterable<AgentToolApprovalEntity> entities) {
        List<AgentToolApprovalEntity> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    Optional<AgentToolApprovalEntity> findByApprovalIdAndTenantId(@Param("approvalId") String approvalId, @Param("tenantId") String tenantId);

    Optional<AgentToolApprovalEntity> findByTenantIdAndRunIdAndToolCallId(@Param("tenantId") String tenantId,
            @Param("runId") String runId,
            @Param("toolCallId") String toolCallId);

    List<AgentToolApprovalEntity> findListPage(@Param("tenantId") String tenantId,
            @Param("status") AgentApprovalDecisionStatus status, @Param("pageable") Pageable pageable);
    long countList(@Param("tenantId") String tenantId, @Param("status") AgentApprovalDecisionStatus status);

    List<AgentToolApprovalEntity> findByDecisionStatusAndExpiresAtBeforeOrderByExpiresAtAsc(@Param("decisionStatus") AgentApprovalDecisionStatus decisionStatus,
            @Param("expiresAt") Instant expiresAt,
            @Param("pageable") Pageable pageable);

    List<AgentToolApprovalEntity> findByDecisionStatusAndExecutionStatusOrderByDecidedAtAsc(@Param("decisionStatus") AgentApprovalDecisionStatus decisionStatus,
            @Param("executionStatus") AgentApprovalExecutionStatus executionStatus,
            @Param("pageable") Pageable pageable);

    int cancelUnfinishedByRun(@Param("tenantId") String tenantId, @Param("runId") String runId, @Param("now") Instant now);

    int decide(@Param("approvalId") String approvalId,
            @Param("tenantId") String tenantId,
            @Param("decisionStatus") AgentApprovalDecisionStatus decisionStatus,
            @Param("executionStatus") AgentApprovalExecutionStatus executionStatus,
            @Param("reviewerUserId") String reviewerUserId,
            @Param("decisionComment") String decisionComment,
            @Param("now") Instant now,
            @Param("expectedVersion") long expectedVersion);

    int expire(@Param("approvalId") String approvalId, @Param("now") Instant now, @Param("expectedVersion") long expectedVersion);

    int startExecution(@Param("approvalId") String approvalId,
            @Param("claimableStatuses") List<AgentApprovalExecutionStatus> claimableStatuses,
            @Param("now") Instant now,
            @Param("expectedVersion") long expectedVersion);

    int completeExecution(@Param("approvalId") String approvalId,
            @Param("targetStatus") AgentApprovalExecutionStatus targetStatus,
            @Param("now") Instant now,
            @Param("expectedVersion") long expectedVersion);
}
