package com.ai.indexing;

import com.ai.util.CryptoUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
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
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CryptoUtil crypto;
    private static final RowMapper<IndexTask> ROW = (rs, n) -> new IndexTask(rs.getString("task_id"),
            IndexTask.Kind.valueOf(rs.getString("kind")), rs.getString("tenant_id"), rs.getString("user_id"),
            rs.getString("source_id"), rs.getString("payload"), rs.getLong("desired_version"),
            rs.getInt("attempts"), rs.getString("lease_owner"));

    public IndexTaskStore(JdbcTemplate jdbc, ObjectMapper mapper, CryptoUtil crypto) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.crypto = crypto;
    }

    /** MANDATORY 防止出现主数据已提交而任务未提交的窗口。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(IndexTask.Kind kind, String tenant, String user, String source, Object payload) {
        require(tenant);
        require(user);
        require(source);
        String body;
        try {
            body = crypto.encrypt(mapper.writeValueAsString(payload));
        } catch (Exception error) {
            throw new IllegalStateException("索引任务序列化失败", error);
        }
        jdbc.update("""
                insert into index_task(task_id,kind,tenant_id,user_id,source_id,payload,desired_version,
                    completed_version,status,attempts,next_attempt_at,updated_at)
                values(?,?,?,?,?,?,1,0,'PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                on duplicate key update payload=values(payload), desired_version=desired_version+1,
                    status=case when lease_owner is null then 'PENDING' else status end, attempts=0,
                    next_attempt_at=CURRENT_TIMESTAMP, last_error=null, updated_at=CURRENT_TIMESTAMP
                """, key(kind, tenant, source), kind.name(), tenant, user, source, body);
    }

    public List<IndexTask> ready(int limit) {
        return jdbc.query("""
                select * from index_task where
                  (status in ('PENDING','RETRY') and next_attempt_at <= CURRENT_TIMESTAMP)
                  or (status='RUNNING' and lease_until < CURRENT_TIMESTAMP)
                order by next_attempt_at limit ?
                """, ROW, limit);
    }

    public IndexTask claim(IndexTask candidate) {
        String owner = UUID.randomUUID().toString();
        int changed = jdbc.update("""
                update index_task set status='RUNNING',lease_owner=?,lease_until=?,updated_at=CURRENT_TIMESTAMP
                where task_id=? and desired_version=? and
                    ((status in ('PENDING','RETRY') and next_attempt_at <= CURRENT_TIMESTAMP)
                      or (status='RUNNING' and lease_until < CURRENT_TIMESTAMP))
                """, owner, leaseDeadline(), candidate.id(), candidate.version());
        return changed == 0 ? null : new IndexTask(candidate.id(), candidate.kind(), candidate.tenantId(),
                candidate.userId(), candidate.sourceId(), candidate.payload(), candidate.version(), candidate.attempts(), owner);
    }

    public boolean renew(IndexTask task) {
        return jdbc.update("update index_task set lease_until=? where task_id=? and lease_owner=? "
                + "and status='RUNNING' and lease_until >= CURRENT_TIMESTAMP",
                leaseDeadline(), task.id(), task.owner()) == 1;
    }

    public void finish(IndexTask task, Throwable error) {
        String state = error == null ? "SUCCEEDED" : task.attempts() + 1 >= MAX_ATTEMPTS ? "FAILED" : "RETRY";
        long backoff = Math.min(300L, 5L << Math.min(task.attempts(), 6));
        int changed = jdbc.update("""
                update index_task set status=case when desired_version=? then ? else 'PENDING' end,
                    completed_version=case when ?='SUCCEEDED' then ? else completed_version end,
                    attempts=case when desired_version=? then ? else 0 end, next_attempt_at=?,
                    last_error=?,lease_owner=null,lease_until=null,updated_at=CURRENT_TIMESTAMP
                where task_id=? and lease_owner=? and lease_until >= CURRENT_TIMESTAMP
                """, task.version(), state, state, task.version(), task.version(), error == null ? 0 : task.attempts() + 1,
                Timestamp.from(Instant.now().plusSeconds(error == null ? 0 : backoff)),
                error == null ? null : error.getClass().getSimpleName(), task.id(), task.owner());
        if (changed == 0) {
            // 失去租约的旧写入可能已触达外部存储，必须让最新版本再收敛一次，不能假装无事发生。
            jdbc.update("update index_task set desired_version=desired_version+1, "
                    + "status=case when lease_owner is null then 'PENDING' else status end,next_attempt_at=CURRENT_TIMESTAMP "
                    + "where task_id=?", task.id());
        }
    }

    public void release(IndexTask task) {
        jdbc.update("update index_task set status='PENDING',lease_owner=null,lease_until=null "
                + "where task_id=? and lease_owner=?", task.id(), task.owner());
    }

    /** 完成的对话任务保留 30 天用于崩溃重放；失败任务保留供排查和重试。 */
    public void purgeCompletedConversations() {
        jdbc.update("delete from index_task where kind='CONVERSATION' and status='SUCCEEDED' "
                + "and lease_owner is null and updated_at < ? limit 100",
                Timestamp.from(Instant.now().minusSeconds(30L * 24 * 3600)));
    }

    public IndexTask.Conversation conversation(IndexTask task) {
        try {
            return mapper.readValue(crypto.decrypt(task.payload()), IndexTask.Conversation.class);
        } catch (Exception error) {
            throw new IllegalStateException("对话索引任务载荷不可读", error);
        }
    }

    public Map<String, IndexTask.Status> statuses(String tenant, String user) {
        return jdbc.query("select source_id,status,desired_version,completed_version,attempts from index_task "
                + "where kind='KNOWLEDGE' and tenant_id=? and user_id=?",
                (rs, n) -> Map.entry(rs.getString(1), new IndexTask.Status(rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getInt(5))),
                tenant, user).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** 人工重试只重置本人失败任务，不能越权重放其他来源。 */
    public boolean retryKnowledge(String tenant, String user, String source) {
        return retry(IndexTask.Kind.KNOWLEDGE, tenant, user, source);
    }

    /** 复用原任务身份与失败态约束，FILE/MEMORY 不新增另一套重试调度。 */
    public boolean retry(IndexTask.Kind kind, String tenant, String user, String source) {
        return jdbc.update("update index_task set status='PENDING',attempts=0,next_attempt_at=CURRENT_TIMESTAMP "
                + "where task_id=? and tenant_id=? and user_id=? and status='FAILED'",
                key(kind, tenant, source), tenant, user) == 1;
    }

    private Timestamp leaseDeadline() {
        return Timestamp.from(Instant.now().plusSeconds(LEASE_SECONDS));
    }

    private void require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("索引身份不能为空");
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
