package com.ai.memory;

import com.ai.background.BackgroundTaskConfiguration;
import com.ai.background.TaskSignalPublisher;
import com.ai.util.CryptoUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 聊天消息与提取任务同事务提交；重复通知不重复建任务，明文不进入 MQ。 */
@Service
public class MemoryExtractionTaskStore {
    private final MemoryExtractionTaskMapper mapper;
    private final ObjectMapper json;
    private final CryptoUtil crypto;
    private final TaskSignalPublisher signals;

    public MemoryExtractionTaskStore(MemoryExtractionTaskMapper mapper, ObjectMapper json,
            CryptoUtil crypto, TaskSignalPublisher signals) {
        this.mapper = mapper;
        this.json = json;
        this.crypto = crypto;
        this.signals = signals;
    }

    /** 来源消息 ID 由本轮主库插入产生，不能由模型或前端指定。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String tenant, String user, String session, String model, long source, long revision,
            String userMessage, String assistantReply) {
        if (source <= 0 || revision < 0 || tenant == null || user == null || session == null) {
            throw new IllegalArgumentException("提取任务来源不完整");
        }
        try {
            String id = UUID.randomUUID().toString();
            String reply = assistantReply == null ? "" : assistantReply;
            var input = new MemoryExtractionTask.Input(userMessage, reply.substring(0, Math.min(1000, reply.length())));
            var task = new MemoryExtractionTask(id, tenant, user, session, model, source, revision,
                    crypto.encrypt(json.writeValueAsString(input)), 0, null);
            if (mapper.insert(task) == 1) signals.afterCommit(BackgroundTaskConfiguration.MEMORY_QUEUE, id);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("提取任务序列化失败", error);
        }
    }

    /** 消息引用只用于定位数据库中的任务，载荷身份以主库为准。 */
    public MemoryExtractionTask claim(String id) {
        MemoryExtractionTask task = mapper.findReady(id);
        if (task == null) return null;
        String owner = UUID.randomUUID().toString();
        if (mapper.claim(id, owner, deadline()) != 1) return null;
        return new MemoryExtractionTask(task.id(), task.tenantId(), task.userId(), task.sessionId(), task.modelId(),
                task.sourceMessageId(), task.revision(), task.payload(), task.attempts() + 1, owner);
    }

    public List<MemoryExtractionTask> ready() { return mapper.ready(100); }
    public boolean renew(MemoryExtractionTask task) { return mapper.renew(task, deadline()) == 1; }

    /** 完成前先落主库，耗尽后由监听器拒绝消息进入死信队列。 */
    public boolean finish(MemoryExtractionTask task, Throwable error) {
        int attempts = task.attempts();
        String state = error == null ? "SUCCEEDED" : attempts >= 5 ? "FAILED" : "RETRY";
        long backoff = Math.min(300, 5L << Math.min(Math.max(0, attempts - 1), 6));
        int changed = mapper.finish(task, state, attempts, Instant.now().plusSeconds(error == null ? 0 : backoff),
                error == null ? null : error.getClass().getSimpleName());
        return changed > 0 && "FAILED".equals(state);
    }

    /** 只在消费者需要时解密，不将原始用户信息输出到日志。 */
    public MemoryExtractionTask.Input input(MemoryExtractionTask task) {
        try {
            return json.readValue(crypto.decrypt(task.payload()), MemoryExtractionTask.Input.class);
        } catch (Exception error) {
            throw new IllegalStateException("提取任务载荷不可读", error);
        }
    }

    /** 已完成载荷立即清空，去重记录保留三十天后分批删除。 */
    public void purge() { mapper.purge(Instant.now().minusSeconds(30L * 86400)); }
    private Instant deadline() { return Instant.now().plusSeconds(60); }
}
