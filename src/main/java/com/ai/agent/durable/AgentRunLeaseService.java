package com.ai.agent.durable;

import com.ai.agent.runtime.AgentRunRegistry;
import com.ai.agent.runtime.AgentRunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 使用 Run 行的状态、版本和过期时间领取与续租恢复任务。
 *
 * @author data-agent
 */
@Service
public class AgentRunLeaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentRunLeaseService.class);
    private static final List<AgentRunStatus> CLAIMABLE = List.of(
            AgentRunStatus.WAITING_APPROVAL,
            AgentRunStatus.RESUMING);

    private final AgentRunStateRepository repository;
    private final AgentDurableRuntimeProperties properties;
    private final AgentRunRegistry runRegistry;
    private final String nodeId = "agent-node-" + UUID.randomUUID();
    private final ConcurrentMap<String, ActiveLease> activeLeases = new ConcurrentHashMap<>();

    public AgentRunLeaseService(AgentRunStateRepository repository,
            AgentDurableRuntimeProperties properties,
            AgentRunRegistry runRegistry) {
        this.repository = repository;
        this.properties = properties;
        this.runRegistry = runRegistry;
    }

    @Transactional
    public Optional<AgentRunLease> claim(String runId, String approvalId) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        AgentRunStateEntity run = repository.findById(runId).orElse(null);
        Instant now = Instant.now();
        if (run == null
                || !Objects.equals(approvalId, run.getApprovalId())
                || run.getResumeAttempts() >= properties.getMaxResumeAttempts()
                || !CLAIMABLE.contains(run.getStatus())
                || (run.getLeaseUntil() != null && !run.getLeaseUntil().isBefore(now))) {
            return Optional.empty();
        }
        Instant leaseUntil = now.plus(properties.getLeaseDuration());
        int changed = repository.claimLease(
                runId,
                CLAIMABLE,
                nodeId,
                leaseUntil,
                now,
                run.getVersion());
        if (changed != 1) {
            return Optional.empty();
        }
        AgentRunStateEntity claimed = repository.findById(runId).orElseThrow();
        ActiveLease active = new ActiveLease(claimed.getVersion(), leaseUntil);
        activeLeases.put(runId, active);
        return Optional.of(new AgentRunLease(runId, nodeId, claimed.getVersion(), leaseUntil));
    }

    @Transactional(readOnly = true)
    public boolean stillOwns(String runId) {
        AgentRunStateEntity run = repository.findById(runId).orElse(null);
        return run != null
                && run.getStatus() == AgentRunStatus.RESUMING
                && Objects.equals(nodeId, run.getLeaseOwner())
                && run.getLeaseUntil() != null
                && run.getLeaseUntil().isAfter(Instant.now());
    }

    public void release(String runId) {
        activeLeases.remove(runId);
    }

    @Scheduled(fixedDelayString = "${app.agent.durable.scan-interval:PT15S}")
    @Transactional
    public void renewActiveLeases() {
        if (!properties.isEnabled()) {
            return;
        }
        Instant now = Instant.now();
        for (var entry : activeLeases.entrySet()) {
            String runId = entry.getKey();
            ActiveLease active = entry.getValue();
            Instant leaseUntil = now.plus(properties.getLeaseDuration());
            int changed = repository.renewLease(
                    runId,
                    nodeId,
                    leaseUntil,
                    now,
                    active.version.get());
            if (changed == 1) {
                AgentRunStateEntity renewed = repository.findById(runId).orElse(null);
                if (renewed != null) {
                    active.version.set(renewed.getVersion());
                    active.leaseUntil = leaseUntil;
                }
            } else {
                activeLeases.remove(runId, active);
                AgentRunStateEntity current = repository.findById(runId).orElse(null);
                if (current != null && current.getStatus() == AgentRunStatus.RESUMING) {
                    runRegistry.cancelSystem(runId, "恢复租约已丢失");
                    LOGGER.warn("Agent Run 恢复租约已丢失并触发节点内取消: runId={}", runId);
                }
            }
        }
    }

    private static final class ActiveLease {
        private final AtomicLong version;
        private volatile Instant leaseUntil;

        private ActiveLease(long version, Instant leaseUntil) {
            this.version = new AtomicLong(version);
            this.leaseUntil = leaseUntil;
        }
    }
}
