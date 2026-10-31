-- 仅取消已停用的完整问答索引任务；保留历史记录与向量，不在启动阶段调用 Milvus。
UPDATE index_task SET status = 'CANCELLED', lease_owner = NULL, lease_until = NULL,
    last_error = 'conversation-indexing-retired', updated_at = CURRENT_TIMESTAMP
WHERE kind = 'CONVERSATION';

-- 索引任务只引用来源主键，旧问答载荷不再有读取入口，移除 NOT NULL 旧列。
ALTER TABLE index_task DROP COLUMN payload,
    ADD INDEX idx_index_task_lease (status, lease_until);

CREATE TABLE memory_write_state (
    tenant_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    last_source_message_id BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, user_id)
);

CREATE TABLE memory_extraction_task (
    task_id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    model_id VARCHAR(128),
    source_message_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    payload MEDIUMTEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_owner VARCHAR(64),
    lease_until TIMESTAMP(6) NULL,
    last_error VARCHAR(128),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_memory_extraction_source (tenant_id, user_id, source_message_id),
    KEY idx_memory_extraction_ready (status, next_attempt_at),
    KEY idx_memory_extraction_lease (status, lease_until),
    KEY idx_memory_extraction_owner (tenant_id, user_id, created_at)
);
