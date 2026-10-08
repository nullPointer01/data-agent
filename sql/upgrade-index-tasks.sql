-- 索引 Outbox。部署未开启 Hibernate ddl-auto=update 时，在启动新版本前执行此建表语句。
-- 将旧 Hibernate 可能生成的原生 ENUM 转成 VARCHAR，允许内部 PLANNING 阶段；不改已有模式值。
ALTER TABLE agent_run_state MODIFY COLUMN mode VARCHAR(32) NOT NULL;
-- 不删除或重建任何现有向量/全文索引。
CREATE TABLE IF NOT EXISTS index_task (
    task_id VARCHAR(64) NOT NULL PRIMARY KEY,
    kind VARCHAR(24) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    payload LONGTEXT NOT NULL,
    desired_version BIGINT NOT NULL,
    completed_version BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_owner VARCHAR(64),
    lease_until DATETIME(6),
    last_error VARCHAR(100),
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_index_task_ready(status,next_attempt_at)
);
