package com.ai.memory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 只在短数据库事务中串行化本人记忆变更，禁止持锁调用模型或向量服务。 */
@Service
public class MemoryWriteGuard {
    private final MemoryWriteStateMapper mapper;

    public MemoryWriteGuard(MemoryWriteStateMapper mapper) {
        this.mapper = mapper;
    }

    /** 请求开始时取得修订快照，后续用户操作会让旧的后台候选失效。 */
    @Transactional
    public long snapshot(String tenant, String user) {
        return lock(tenant, user).revision();
    }

    /** 模型调用前廉价跳过已失效任务；真正写入仍必须在锁内重新验证。 */
    public boolean current(MemoryExtractionTask task) {
        var state = mapper.find(task.tenantId(), task.userId());
        return state != null && state.revision() == task.revision()
                && state.lastSourceMessageId() < task.sourceMessageId();
    }

    /** 获取用户级写锁；所有语义写入和显式删除使用相同锁顺序。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryWriteStateMapper.State lock(String tenant, String user) {
        if (tenant == null || tenant.isBlank() || user == null || user.isBlank()) {
            throw new IllegalArgumentException("记忆变更需要完整身份");
        }
        mapper.ensure(tenant, user);
        return mapper.lock(tenant, user);
    }

    /** 显式保存/修正/删除使之前生成的后台候选失效，避免旧任务复活被删除数据。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void advance(String tenant, String user) {
        lock(tenant, user);
        mapper.advance(tenant, user);
    }

    /** 处理较新消息后不允许迟到的旧任务覆盖，空候选也推进消费水位。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void processed(String tenant, String user, long source) {
        mapper.processed(tenant, user, source);
    }
}
