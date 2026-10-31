package com.ai.indexing;

import com.ai.background.BackgroundTaskConfiguration;
import com.ai.background.TaskSignalPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 持久索引任务：与来源数据同事务写入，同一来源合并到最新版本。
 * 工作线程以租约令牌提交结果；过期版本不能把新任务标为完成。跨外部存储采用幂等补偿。
 */
@Service
public class IndexTaskStore {
    private static final int MAX_ATTEMPTS = 5;
    private static final long LEASE_SECONDS = 60;
    private final IndexTaskMapper taskMapper;
    private final TaskSignalPublisher signals;

    public IndexTaskStore(IndexTaskMapper taskMapper, TaskSignalPublisher signals) {
        this.taskMapper = taskMapper;
        this.signals = signals;
    }

    /** MANDATORY 防止出现主数据已提交而任务未提交的窗口。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(IndexTask.Kind kind, String tenant, String user, String source) {
        require(tenant);
        require(user);
        require(source);
        String id = key(kind, tenant, source);
        taskMapper.enqueue(id, kind, tenant, user, source);
        signals.afterCommit(BackgroundTaskConfiguration.INDEX_QUEUE, id);
    }

    public List<IndexTask> ready(int limit) {
        return taskMapper.ready(limit);
    }

    /** 消息可能重复或迟到，只读取当前可领取版本，终态和已持有租约的任务不重复执行。 */
    public IndexTask findReady(String id) {
        return taskMapper.findReady(id);
    }

    public IndexTask claim(IndexTask candidate) {
        String owner = UUID.randomUUID().toString();
        int changed = taskMapper.claim(candidate, owner, leaseDeadline());
        return changed == 0 ? null : new IndexTask(candidate.id(), candidate.kind(), candidate.tenantId(),
                candidate.userId(), candidate.sourceId(), candidate.version(), candidate.attempts() + 1, owner);
    }

    public boolean renew(IndexTask task) {
        return taskMapper.renew(task, leaseDeadline()) == 1;
    }

    public boolean finish(IndexTask task, Throwable error) {
        String state = error == null ? "SUCCEEDED" : task.attempts() >= MAX_ATTEMPTS ? "FAILED" : "RETRY";
        long backoff = Math.min(300L, 5L << Math.min(Math.max(0, task.attempts() - 1), 6));
        int changed = taskMapper.finish(task, state, task.attempts(),
                Instant.now().plusSeconds(error == null ? 0 : backoff),
                error == null ? null : error.getClass().getSimpleName());
        if (changed == 0) {
            // 失去租约的旧写入可能已触达外部存储，必须让最新版本再收敛一次，不能假装无事发生。
            taskMapper.markDirty(task.id());
        }
        return changed > 0 && "FAILED".equals(state) && taskMapper.failedVersion(task);
    }

    /** 知识列表仅装配本页索引状态，避免分页接口仍读取全部后台任务。 */
    public Map<String, IndexTask.Status> knowledgeStatuses(String tenant, String user, List<String> ids) {
        if (ids.isEmpty()) return Map.of();
        return taskMapper.knowledgeStatuses(tenant, user, ids).stream()
                .collect(Collectors.toMap(IndexTaskMapper.StateRow::sourceId, IndexTaskMapper.StateRow::status));
    }

    public Map<String, String> memoryStatuses(String tenant, String user, List<String> ids) {
        if (ids.isEmpty()) return Map.of();
        return taskMapper.memoryStatuses(tenant, user, ids).stream()
                .collect(Collectors.toMap(IndexTaskMapper.StateRow::sourceId, row ->
                        "SUCCEEDED".equals(row.state()) && row.completedVersion() < row.desiredVersion()
                                ? "PENDING" : row.state()));
    }

    /** 人工重试只重置本人失败任务，不能越权重放其他来源。 */
    @Transactional
    public boolean retryKnowledge(String tenant, String user, String source) {
        return retry(IndexTask.Kind.KNOWLEDGE, tenant, user, source);
    }

    /** 复用原任务身份与失败态约束，FILE/MEMORY 不新增另一套重试调度。 */
    @Transactional
    public boolean retry(IndexTask.Kind kind, String tenant, String user, String source) {
        String id = key(kind, tenant, source);
        boolean changed = taskMapper.retry(id, tenant, user) == 1;
        if (changed) signals.afterCommit(BackgroundTaskConfiguration.INDEX_QUEUE, id);
        return changed;
    }

    private Instant leaseDeadline() {
        return Instant.now().plusSeconds(LEASE_SECONDS);
    }

    private void require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("索引身份不能为空");
        }
    }
    private String key(IndexTask.Kind kind, String tenant, String source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((kind.name() + "\0" + tenant + "\0" + source).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
